import griffon.core.GriffonApplication
import griffon.core.env.Metadata
import groovy.util.logging.Log
import net.i2p.util.SystemVersion

import org.codehaus.griffon.runtime.core.AbstractLifecycleHandler

import com.muwire.core.Core
import com.muwire.core.MuWireSettings
import com.muwire.core.files.FileSharedEvent

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.swing.JFileChooser
import javax.swing.JOptionPane

import static griffon.util.GriffonApplicationUtils.isMacOSX
import static groovy.swing.SwingBuilder.lookAndFeel

import java.beans.PropertyChangeEvent
import java.util.logging.Level

@Log
class Ready extends AbstractLifecycleHandler {
    
    @Inject Metadata metadata
    
    @Inject
    Ready(@Nonnull GriffonApplication application) {
        super(application)
    }

    @Override
    void execute() {
        log.info "starting core services"
        def portableHome = System.getProperty("portable.home")
        def home = portableHome == null ? 
            selectHome() :
            portableHome
            
        home = new File(home)
        if (!home.exists()) {
            log.info("creating home dir $home")
            home.mkdirs()
        }
        
        def props = new Properties()
        def propsFile = new File(home, "MuWire.properties")
        if (propsFile.exists()) {
            log.info("loading existing props file")
            propsFile.withInputStream {
                props.load(it)
            }
            props = new MuWireSettings(props)
        } else {
            log.info("creating new properties")
            props = new MuWireSettings()
            def nickname
            while (true) {
                nickname = JOptionPane.showInputDialog(null,
                        "Your nickname is displayed when you send search results so other MuWire users can choose to trust you",
                        "Please choose a nickname", JOptionPane.PLAIN_MESSAGE)
                if (nickname == null || nickname.trim().length() == 0) {
                    JOptionPane.showMessageDialog(null, "Nickname cannot be empty", "Select another nickname", 
                        JOptionPane.WARNING_MESSAGE)
                    continue
                }
                if (nickname.contains("@")) {
                    JOptionPane.showMessageDialog(null, "Nickname cannot contain @, choose another", 
                        "Select another nickname", JOptionPane.WARNING_MESSAGE)
                    continue
                }
                nickname = nickname.trim()
                break
            }
            props.setNickname(nickname)
            
            
            def portableDownloads = System.getProperty("portable.downloads")
            if (portableDownloads != null) {
                props.downloadLocation = new File(portableDownloads)
            } else {
                def chooser = new JFileChooser()
                chooser.setDialogTitle("Select a directory where downloads will be saved")
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
                int rv = chooser.showOpenDialog(null)
                if (rv != JFileChooser.APPROVE_OPTION) {
                    JOptionPane.showMessageDialog(null, "MuWire will now exit")
                    System.exit(0)
                }
                props.downloadLocation = chooser.getSelectedFile()
            }
            
            propsFile.withOutputStream {
                props.write(it)
            }
        }
        
        Core core
        try {
            core = new Core(props, home, metadata["application.version"])
        } catch (Exception bad) {
            log.log(Level.SEVERE,"couldn't initialize core",bad)
            JOptionPane.showMessageDialog(null, "Couldn't connect to I2P router.  Make sure I2P is running and restart MuWire",
                "Can't connect to I2P router", JOptionPane.WARNING_MESSAGE)
            System.exit(0)
        }
        core.startServices()
        application.context.put("muwire-settings", props)
        application.context.put("core",core)
        application.getPropertyChangeListeners("core").each { 
            it.propertyChange(new PropertyChangeEvent(this, "core", null, core)) 
        }
        
        if (props.sharedFiles != null) {
            props.sharedFiles.split(",").each {
                core.eventBus.publish(new FileSharedEvent(file : new File(it)))
            }
        }
    }
    
    private static String selectHome() {
        def home = new File(System.properties["user.home"])
        def defaultHome = new File(home, ".MuWire")
        if (defaultHome.exists())
            return defaultHome.getAbsolutePath()
        if (SystemVersion.isMac()) { 
            def library = new File(home, "Library")
            def appSupport = new File(library, "Application Support")
            def muwire = new File(appSupport,"MuWire")
            return muwire.getAbsolutePath()
        }
        if (SystemVersion.isWindows()) {
            def appData = new File(home,"AppData")
            def roaming = new File(appData, "Roaming")
            def muwire = new File(roaming, "MuWire")
            return muwire.getAbsolutePath()
        }
        defaultHome.getAbsolutePath()
    }
}

