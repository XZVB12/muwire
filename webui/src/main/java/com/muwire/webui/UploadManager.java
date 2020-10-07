package com.muwire.webui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.muwire.core.Core;
import com.muwire.core.upload.UploadEvent;
import com.muwire.core.upload.UploadFinishedEvent;
import com.muwire.core.upload.Uploader;
import com.muwire.core.util.BandwidthCounter;

public class UploadManager {
    
    private final Core core;
    private final List<UploaderWrapper> uploads = Collections.synchronizedList(new ArrayList<>());
    
    public UploadManager(Core core) {
        this.core = core;
    }
    
    public List<UploaderWrapper> getUploads() {
        return uploads;
    }
    
    public void onUploadEvent(UploadEvent e) {
        UploaderWrapper wrapper = null;
        synchronized(uploads) {
            for(UploaderWrapper uw : uploads) {
                if (uw.uploader.equals(e.getUploader())) {
                    wrapper = uw;
                    break;
                }
            }
        }
        if (wrapper != null) {
            wrapper.updateUploader(e.getUploader());
        } else {
            wrapper = new UploaderWrapper();
            wrapper.uploader = e.getUploader();
            uploads.add(wrapper);
        }
    }
    
    public void onUploadFinishedEvent(UploadFinishedEvent e) {
        UploaderWrapper wrapper = null;
        synchronized(uploads) {
            for(UploaderWrapper uw : uploads) {
                if (uw.uploader.equals(e.getUploader())) {
                    wrapper = uw;
                    break;
                }
            }
        }
        if (wrapper != null)
            wrapper.finished = true;
    }
    
    public void clearFinished() {
        synchronized(uploads) {
            for(Iterator<UploaderWrapper> iter = uploads.iterator(); iter.hasNext();) {
                UploaderWrapper wrapper = iter.next();
                if (wrapper.finished)
                    iter.remove();
            }
        }
    }
    
    public class UploaderWrapper {
        private volatile Uploader uploader;
        private volatile int requests;
        private volatile boolean finished;
        
        private volatile BandwidthCounter bwCounter = new BandwidthCounter(0);
        
        public Uploader getUploader() {
            return uploader;
        }
        
        public int getRequests() {
            return requests;
        }
        
        public boolean isFinished() {
            return finished;
        }
        
        public int speed() {
            if (finished)
                return 0;
            
            initIfNeeded();
            bwCounter.read(uploader.dataSinceLastRead());
            return bwCounter.average();
        }
        
        void updateUploader(Uploader uploader) {
            initIfNeeded();
            bwCounter.read(this.uploader.dataSinceLastRead());
            this.uploader = uploader;
            requests++;
            finished = false;
        }
        
        private void initIfNeeded() {
            if (bwCounter.getMemory() != core.getMuOptions().getSpeedSmoothSeconds())
                bwCounter = new BandwidthCounter(core.getMuOptions().getSpeedSmoothSeconds());
        }
    }
}
