package com.muwire.webui;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.muwire.core.Core;
import com.muwire.core.InfoHash;
import com.muwire.core.download.Downloader;
import com.muwire.core.download.UIDownloadCancelledEvent;
import com.muwire.core.download.UIDownloadEvent;
import com.muwire.core.search.UIResultEvent;

import net.i2p.data.Base64;
import net.i2p.data.DataHelper;

public class DownloadServlet extends HttpServlet {

    private DownloadManager downloadManager;
    private SearchManager searchManager;
    private Core core;
    
    public void init(ServletConfig config) throws ServletException {
        downloadManager = (DownloadManager) config.getServletContext().getAttribute("downloadManager");
        searchManager = (SearchManager) config.getServletContext().getAttribute("searchManager");
        core = (Core) config.getServletContext().getAttribute("core");
    }
    
    
    
    @Override
    public void destroy() {
        if (downloadManager != null)
            downloadManager.shutdown();
    }



    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (downloadManager == null) {
            resp.sendError(403, "Not initialized");
            return;
        }
        String section = req.getParameter("section");
        
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8'?>");
        if (section.equals("list")) {
            List<Download> downloads = new ArrayList<>();
            downloadManager.getDownloaders().forEach(d -> {

                int speed = d.speed();
                long ETA = Long.MAX_VALUE;
                if (speed > 0) 
                    ETA = (d.getNPieces() - d.donePieces()) * d.getPieceSize() * 1000 / speed;

                int percent = -1;
                if (d.getNPieces() != 0)
                    percent = (int)(d.donePieces() * 100 / d.getNPieces());

                Download download = new Download(d.getInfoHash(),
                        d.getFile().getName(),
                        d.getCurrentState(),
                        speed,
                        ETA,
                        percent,
                        d.getLength());

                downloads.add(download);
            });
            COMPARATORS.sort(downloads, req);


            sb.append("<Downloads>");
            downloads.forEach(d -> d.toXML(sb));
            sb.append("</Downloads>");
        } else if (section.equals("details")) {
            String infoHashB64 = req.getParameter("infoHash");
            InfoHash infoHash;
            try {
                infoHash = new InfoHash(Base64.decode(infoHashB64));
            } catch (Exception bad) {
                resp.sendError(403, "Bad param");
                return;
            }
            Optional<Downloader> optional = downloadManager.getDownloaders().filter(d -> d.getInfoHash().equals(infoHash)).findFirst();
            if (optional.isPresent()) {
                Downloader downloader = optional.get();
                
                sb.append("<Details>");
                sb.append("<Path>").append(Util.escapeHTMLinXML(downloader.getFile().getAbsolutePath())).append("</Path>");
                sb.append("<PieceSize>").append(downloader.getPieceSize()).append("</PieceSize>");
                sb.append("<Sequential>").append(downloader.isSequential()).append("</Sequential>");
                sb.append("<KnownSources>").append(downloader.getTotalWorkers()).append("</KnownSources>");
                sb.append("<ActiveSources>").append(downloader.activeWorkers()).append("</ActiveSources>");
                sb.append("<HopelessSources>").append(downloader.countHopelessSources()).append("</HopelessSources>");
                sb.append("<TotalPieces>").append(downloader.getNPieces()).append("</TotalPieces>");
                sb.append("<DonePieces>").append(downloader.donePieces()).append("</DonePieces>");
                sb.append("</Details>");
            } else {
                resp.sendError(404, "Not found");
                return;
            }
        }
        
        resp.setContentType("text/xml");
        resp.setCharacterEncoding("UTF-8");
        resp.setDateHeader("Expires", 0);
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Cache-Control", "no-store, max-age=0, no-cache, must-revalidate");
        byte[] out = sb.toString().getBytes("UTF-8");
        resp.setContentLength(out.length);
        resp.getOutputStream().write(out);
    }



    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if (action == null) {
            resp.sendError(403, "Bad param");
            return;
        }
        if (action.equals("clear")) {
            downloadManager.clearFinished();
            return;
        }
        
        String infoHashB64 = req.getParameter("infoHash");
        if (infoHashB64 == null) {
            resp.sendError(403, "Bad param");
            return;
        }
        byte[] h = Base64.decode(infoHashB64);
        if (h == null || h.length != InfoHash.SIZE) {
            resp.sendError(403, "Bad param");
            return;
        }
        InfoHash infoHash = new InfoHash(h);
        if (action.equals("start")) {
            if (core == null) {
                resp.sendError(403, "Not initialized");
                return;
            }
            UUID uuid = UUID.fromString(req.getParameter("uuid"));
            Set<UIResultEvent> results = searchManager.getResults().get(uuid).getByInfoHash(infoHash);
            
            UIDownloadEvent event = new UIDownloadEvent();
            UIResultEvent[] resultsArray = results.toArray(new UIResultEvent[0]);
            event.setResult(resultsArray);
            // TODO: sequential
            event.setSources(searchManager.getResults().get(uuid).getPossibleSources(infoHash));
            event.setTarget(new File(core.getMuOptions().getDownloadLocation(), resultsArray[0].getName()));
            core.getEventBus().publish(event);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        } else if (action.equals("cancel")) {
            if (downloadManager == null) {
                resp.sendError(403, "Not initialized");
                return;
            }
            downloadManager.cancel(infoHash);
        } else if (action.equals("pause")) {
            if (core == null) {
                resp.sendError(403, "Not initialized");
                return;
            }
            downloadManager.pause(infoHash);
        } else if (action.equals("resume")) {
            if (core == null) {
                resp.sendError(403, "Not initialized");
                return;
            }
            downloadManager.resume(infoHash);
        }
        // P-R-G
        resp.sendRedirect("/MuWire/Downloads");
    }
    
    private static class Download {
        private final InfoHash infoHash;
        private final String name;
        private final Downloader.DownloadState state;
        private final int speed;
        private final long ETA;
        private final int percent;
        private final long totalSize;
        
        Download(InfoHash infoHash, String name, Downloader.DownloadState state,
                int speed, long ETA, int percent, long totalSize) {
            this.infoHash = infoHash;
            this.name = name;
            this.state = state;
            this.speed = speed;
            this.ETA = ETA;
            this.percent = percent;
            this.totalSize = totalSize;
        }
        
        void toXML(StringBuilder sb) {
            sb.append("<Download>");
            sb.append("<InfoHash>").append(Base64.encode(infoHash.getRoot())).append("</InfoHash>");
            sb.append("<Name>").append(Util.escapeHTMLinXML(name)).append("</Name>");
            sb.append("<State>").append(state.toString()).append("</State>");
            sb.append("<Speed>").append(DataHelper.formatSize2Decimal(speed, false)).append("B/sec").append("</Speed>");
            String ETAString;
            if (ETA == Long.MAX_VALUE)
                ETAString = Util._t("Unknown");
            else
                ETAString = DataHelper.formatDuration(ETA);
            sb.append("<ETA>").append(ETAString).append("</ETA>");
            String progress = String.format("%2d", percent) + "% of "+DataHelper.formatSize2Decimal(totalSize, false) + "B";
            sb.append("<Progress>").append(progress).append("</Progress>");
            sb.append("</Download>");
        }
    }
    
    private static final Comparator<Download> BY_NAME = (l, r) -> {
        return Collator.getInstance().compare(l.name, r.name);
    };
    
    private static final Comparator<Download> BY_STATE = (l, r) -> {
        return Collator.getInstance().compare(l.state.toString(), r.state.toString());
    };
    
    private static final Comparator<Download> BY_SPEED = (l, r) -> {
        return Integer.compare(l.speed, r.speed);
    };
    
    private static final Comparator<Download> BY_ETA = (l, r) -> {
        return Long.compare(l.ETA, r.ETA);
    };
    
    private static final Comparator<Download> BY_PROGRESS = (l, r) -> {
        return Integer.compare(l.percent, r.percent);
    };
    
    private static final ColumnComparators<Download> COMPARATORS = new ColumnComparators<>();
    static {
        COMPARATORS.add("Name", BY_NAME);
        COMPARATORS.add("State", BY_STATE);
        COMPARATORS.add("Speed", BY_SPEED);
        COMPARATORS.add("ETA", BY_ETA);
        COMPARATORS.add("Progress", BY_PROGRESS);
    }
}
