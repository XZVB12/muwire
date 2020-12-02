package com.muwire.webui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import com.muwire.core.Core;
import com.muwire.core.MuWireSettings;
import com.muwire.core.UILoadedEvent;
import com.muwire.core.connection.ConnectionEvent;
import com.muwire.core.connection.DisconnectionEvent;
import com.muwire.core.download.DownloadStartedEvent;
import com.muwire.core.filecert.CertificateFetchEvent;
import com.muwire.core.filecert.CertificateFetchedEvent;
import com.muwire.core.filefeeds.FeedFetchEvent;
import com.muwire.core.filefeeds.FeedItemFetchedEvent;
import com.muwire.core.filefeeds.FeedLoadedEvent;
import com.muwire.core.filefeeds.UIFeedConfigurationEvent;
import com.muwire.core.files.AllFilesLoadedEvent;
import com.muwire.core.files.DirectoryUnsharedEvent;
import com.muwire.core.files.DirectoryWatchedEvent;
import com.muwire.core.files.FileDownloadedEvent;
import com.muwire.core.files.FileHashedEvent;
import com.muwire.core.files.FileHashingEvent;
import com.muwire.core.files.FileLoadedEvent;
import com.muwire.core.files.FileSharedEvent;
import com.muwire.core.files.FileUnsharedEvent;
import com.muwire.core.files.directories.WatchedDirectorySyncEvent;
import com.muwire.core.search.BrowseStatusEvent;
import com.muwire.core.search.UIResultBatchEvent;
import com.muwire.core.search.UIResultEvent;
import com.muwire.core.trust.TrustEvent;
import com.muwire.core.trust.TrustSubscriptionEvent;
import com.muwire.core.trust.TrustSubscriptionUpdatedEvent;
import com.muwire.core.upload.UploadEvent;
import com.muwire.core.upload.UploadFinishedEvent;

import net.i2p.app.ClientAppManager;
import net.i2p.app.ClientAppState;
import net.i2p.router.RouterContext;
import net.i2p.router.app.RouterApp;
import net.i2p.util.Log;

public class MuWireClient {
    
    private final RouterContext ctx;
    private final ServletContext servletContext;
    private final String version;
    private final String home;
    private final File mwProps, webUIProps;
    
    private volatile Core core;
    private volatile boolean coreLoaded;
    
    public MuWireClient(RouterContext ctx, String home, String version, ServletContext servletContext) {
        this.ctx = ctx;
        this.servletContext = servletContext;
        this.version = version;
        this.home = home;
        this.mwProps = new File(home, "MuWire.properties");
        this.webUIProps = new File(home, "webui.properties");
    }

    public void start() throws Throwable {
        if (needsMWInit())
            return;
        
        Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
        while(loggerNames.hasMoreElements()) {
            String name = loggerNames.nextElement();
            Logger logger = LogManager.getLogManager().getLogger(name);
            for (Handler h : logger.getHandlers()) {
                logger.removeHandler(h);
                h.close();
            }
            logger.addHandler(new I2PLogHandler(ctx));
        }
        
        BufferedReader reader;
        Properties props = new Properties();

        if (webUIProps.exists() && webUIProps.isFile()) {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(webUIProps), StandardCharsets.UTF_8));
            props.load(reader);
            reader.close();
        }
        WebUISettings webSettings = new WebUISettings(props);
        servletContext.setAttribute("webSettings", webSettings);
        
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(mwProps), StandardCharsets.UTF_8));
        props = new Properties();
        props.load(reader);
        reader.close();
        
        MuWireSettings settings = new MuWireSettings(props);
        settings.setAllowMessages(false);
        Core core = new Core(settings, new File(home), version);
        setCore(core);
        MWStarter starter = new MWStarter(core, this);
        starter.start();
    }
    
    public void stop() throws Throwable {
        Core core = this.core;
        if (core == null)
            return;
        core.shutdown();
        this.core = null;
    }
    
    
    
    public boolean needsMWInit() {
        return !mwProps.exists();
    }
    
    public void initMWProps(String nickname, File downloadLocation, File incompleteLocation, File dropBoxLocation) throws Exception {
        if (!downloadLocation.exists())
            downloadLocation.mkdirs();
        if (!incompleteLocation.exists())
            incompleteLocation.mkdirs();
        if (!dropBoxLocation.exists())
            dropBoxLocation.mkdirs();
        
        MuWireSettings settings = new MuWireSettings();
        settings.setNickname(nickname);
        settings.setDownloadLocation(downloadLocation);
        settings.setIncompleteLocation(incompleteLocation);
        settings.setPlugin(true);
        
        PrintWriter pw = new PrintWriter(mwProps, "UTF-8");
        settings.write(pw);
        pw.close();
        
        WebUISettings webSettings = new WebUISettings();
        webSettings.setDropBoxLocation(dropBoxLocation);
        
        pw = new PrintWriter(webUIProps, "UTF-8");
        webSettings.write(pw);
        pw.close();
    }
    
    public Core getCore() {
        return core;
    }
    
    public ServletContext getServletContext() {
        return servletContext;
    }
    
    void setCore(Core core) {
        this.core = core;
        servletContext.setAttribute("core", core);
        
        core.getEventBus().register(AllFilesLoadedEvent.class, this);
        
        SearchManager searchManager = new SearchManager(core);
        core.getEventBus().register(UIResultBatchEvent.class, searchManager);
        
        DownloadManager downloadManager = new DownloadManager(core);
        core.getEventBus().register(DownloadStartedEvent.class, downloadManager);
        
        ConnectionCounter connectionCounter = new ConnectionCounter();
        core.getEventBus().register(ConnectionEvent.class, connectionCounter);
        core.getEventBus().register(DisconnectionEvent.class, connectionCounter);
        
        FileManager fileManager = new FileManager(core);
        core.getEventBus().register(FileLoadedEvent.class, fileManager);
        core.getEventBus().register(FileHashedEvent.class, fileManager);
        core.getEventBus().register(FileDownloadedEvent.class, fileManager);
        core.getEventBus().register(FileHashingEvent.class, fileManager);
        core.getEventBus().register(FileUnsharedEvent.class, fileManager);
        core.getEventBus().register(DirectoryUnsharedEvent.class, fileManager);
        core.getEventBus().register(AllFilesLoadedEvent.class, fileManager);
        
        BrowseManager browseManager = new BrowseManager(core);
        core.getEventBus().register(BrowseStatusEvent.class, browseManager);
        core.getEventBus().register(UIResultBatchEvent.class, browseManager);
        
        TrustManager trustManager = new TrustManager();
        core.getEventBus().register(TrustEvent.class, trustManager);
        core.getEventBus().register(TrustSubscriptionUpdatedEvent.class, trustManager);
        
        CertificateManager certificateManager = new CertificateManager(core, fileManager);
        core.getEventBus().register(CertificateFetchedEvent.class, certificateManager);
        core.getEventBus().register(CertificateFetchEvent.class, certificateManager);
        
        UploadManager uploadManager = new UploadManager(core);
        core.getEventBus().register(UploadEvent.class, uploadManager);
        core.getEventBus().register(UploadFinishedEvent.class, uploadManager);
        
        FeedManager feedManager = new FeedManager(core, fileManager);
        core.getEventBus().register(FeedLoadedEvent.class, feedManager);
        core.getEventBus().register(UIFeedConfigurationEvent.class, feedManager);
        core.getEventBus().register(FeedFetchEvent.class, feedManager);
        core.getEventBus().register(FeedItemFetchedEvent.class, feedManager);
        
        AdvancedSharingManager advancedSharingManager = new AdvancedSharingManager(core);
        core.getEventBus().register(DirectoryWatchedEvent.class, advancedSharingManager);
        core.getEventBus().register(DirectoryUnsharedEvent.class, advancedSharingManager);
        core.getEventBus().register(WatchedDirectorySyncEvent.class, advancedSharingManager);
        
        servletContext.setAttribute("searchManager", searchManager);
        servletContext.setAttribute("downloadManager", downloadManager);
        servletContext.setAttribute("connectionCounter", connectionCounter);
        servletContext.setAttribute("fileManager", fileManager);
        servletContext.setAttribute("browseManager", browseManager);
        servletContext.setAttribute("trustManager", trustManager);
        servletContext.setAttribute("certificateManager", certificateManager);
        servletContext.setAttribute("uploadManager", uploadManager);
        servletContext.setAttribute("feedManager", feedManager);
        servletContext.setAttribute("advancedSharingManager", advancedSharingManager);
    }
    
    public String getHome() {
        return home;
    }
    
    void setCoreLoaded() {
        coreLoaded = true;
    }
    
    public boolean isCoreLoaded() {
        return coreLoaded;
    }
    
    public void onAllFilesLoadedEvent(AllFilesLoadedEvent e) {
        
        core.getMuOptions().getTrustSubscriptions().forEach( p -> {
            TrustSubscriptionEvent event = new TrustSubscriptionEvent();
            event.setPersona(p);
            event.setSubscribe(true);
            core.getEventBus().publish(event);
        });
    }
}
