package com.muwire.gui

import griffon.core.artifact.GriffonController
import griffon.core.controller.ControllerAction
import griffon.inject.MVCMember
import griffon.metadata.ArtifactProviderFor
import net.i2p.data.Base64

import javax.annotation.Nonnull

import com.muwire.core.SharedFile
import com.muwire.core.collections.FileCollection
import com.muwire.core.collections.UICollectionDeletedEvent
import com.muwire.core.util.DataUtil

@ArtifactProviderFor(GriffonController)
class CollectionsToolController {
    @MVCMember @Nonnull
    CollectionsToolModel model
    @MVCMember @Nonnull
    CollectionsToolView view

    @ControllerAction
    void delete() {
        int row = view.selectedCollectionRow()
        if (row < 0)
            return
        FileCollection collection = model.collections.get(row)
        UICollectionDeletedEvent e = new UICollectionDeletedEvent(collection : collection)
        model.eventBus.publish(e)
        model.collections.remove(row)
        view.collectionsTable.model.fireTableDataChanged()
        view.clearFilesTable()
    }
    
    @ControllerAction
    void close() {
        view.dialog.setVisible(false)
        mvcGroup.destroy()
    }
    
    @ControllerAction
    void viewComment() {
        int row = view.selectedCollectionRow()
        if (row < 0)
            return
        FileCollection collection = model.collections.get(row)
        
        def params = [:]
        params['text'] = collection.comment
        mvcGroup.createMVCGroup("show-comment", params)
    }
    
    @ControllerAction
    void viewFileComment() {
        int row = view.selectedFileRow()
        if (row < 0)
            return
        SharedFile sf = model.files.getAt(row)
        
        def params = [:]
        params['text'] = DataUtil.readi18nString(Base64.decode(sf.getComment()))
        mvcGroup.createMVCGroup("show-comment", params)
    }
}