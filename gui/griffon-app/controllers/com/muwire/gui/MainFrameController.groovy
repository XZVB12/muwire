package com.muwire.gui

import griffon.core.GriffonApplication
import griffon.core.artifact.GriffonController
import griffon.core.controller.ControllerAction
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import javax.annotation.Nonnull
import javax.inject.Inject

import com.muwire.core.Core
import com.muwire.core.download.DownloadStartedEvent
import com.muwire.core.download.UIDownloadEvent
import com.muwire.core.search.QueryEvent
import com.muwire.core.search.SearchEvent
import com.muwire.core.trust.TrustEvent
import com.muwire.core.trust.TrustLevel

@ArtifactProviderFor(GriffonController)
class MainFrameController {
    @Inject @Nonnull GriffonApplication application
    @MVCMember @Nonnull
    FactoryBuilderSupport builder
    
    @MVCMember @Nonnull
    MainFrameModel model

    private volatile Core core
    
    @ControllerAction
    void search() {
        def search = builder.getVariable("search-field").text
        def searchEvent = new SearchEvent(searchTerms : [search], uuid : UUID.randomUUID())
        core.eventBus.publish(new QueryEvent(searchEvent : searchEvent, firstHop : true, 
            replyTo: core.me.destination, receivedOn: core.me.destination))
    }
    
    private def selectedResult() {
        def resultsTable = builder.getVariable("results-table")
        int row = resultsTable.getSelectedRow()
        model.results[row]
    }
    
    @ControllerAction
    void download() {
        def result = selectedResult()
        def file = new File(application.context.get("muwire-settings").downloadLocation, result.name) 
        core.eventBus.publish(new UIDownloadEvent(result : result, target : file))
    }
    
    @ControllerAction
    void trust() {
        def result = selectedResult()
        core.eventBus.publish( new TrustEvent(destination : result.sender.destination, level : TrustLevel.TRUSTED))
    }
    
    @ControllerAction
    void distrust() {
        def result = selectedResult()
        core.eventBus.publish( new TrustEvent(destination : result.sender.destination, level : TrustLevel.DISTRUSTED))
    }
    
    void mvcGroupInit(Map<String, String> args) {
        application.addPropertyChangeListener("core", {e-> 
            core = e.getNewValue()
        })
    }
}