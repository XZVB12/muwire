package com.muwire.core.collections

import com.muwire.core.Constants
import com.muwire.core.InfoHash
import com.muwire.core.Name
import com.muwire.core.Persona
import com.muwire.core.util.DataUtil

import net.i2p.crypto.DSAEngine
import net.i2p.data.Signature
import net.i2p.data.SigningPrivateKey

class FileCollection {
    
    private final long timestamp
    private final Persona author
    private final String comment
    private final byte[] sig
    
    private volatile byte[] payload
    
    private final Set<FileCollectionItem> files = new LinkedHashSet<>()
    
    final PathTree tree
    
    FileCollection(long timestamp, Persona author, String comment, Set<FileCollectionItem> files,
        SigningPrivateKey spk) {
        this.timestamp = timestamp;
        this.author = author;
        this.comment = comment
        this.files = files
        
        tree = new PathTree(files.first().pathElements.first())
        for(FileCollectionItem item : files) {
            tree.add(item.pathElements)
        }
        
        byte [] signablePayload = signablePayload()
        Signature signature = DSAEngine.getInstance().sign(signablePayload, spk)
        this.sig = signature.getData()
    }
    
    FileCollection(InputStream is) {
        DataInputStream dis = new DataInputStream(is)
        byte version = (byte) dis.read()
        if (version != Constants.COLLECTION_VERSION)
            throw new InvalidCollectionException("unsupported version $version")
        
        int numFiles = dis.readUnsignedShort()
        
        author = new Persona(dis)
        timestamp = dis.readLong()
        def commentName = new Name(dis)
        comment = commentName.name
        
        numFiles.times { 
            files.add(new FileCollectionItem(dis))
        }
        
        sig = new byte[Constants.SIG_TYPE.getSigLen()]
        dis.readFully(sig)
        
        if (!verify())
            throw new InvalidCollectionException("invalid signature")
    }
    
    private boolean verify() {
        def baos = new ByteArrayOutputStream()
        def daos = new DataOutputStream(baos)
        
        daos.writeByte(Constants.COLLECTION_VERSION)
        daos.writeShort(files.size())
        author.write(daos)
        daos.writeLong(timestamp)
        def commentName = new Name(comment)
        commentName.write(daos)
        files.each { 
            it.write(daos)
        }
        
        daos.close()
        byte [] payload = baos.toByteArray()
        
        def spk = author.destination.getSigningPublicKey()
        def signature = new Signature(spk.getType(), sig)
        DSAEngine.getInstance().verifySignature(signature, payload, spk)
    }
    
    private byte[] signablePayload() {
        
        def baos = new ByteArrayOutputStream()
        def daos = new DataOutputStream(baos)
        
        daos.writeByte(Constants.COLLECTION_VERSION)
        daos.writeShort(files.size())
        author.write(daos)
        daos.writeLong(timestamp)
        def commentName = new Name(comment)
        commentName.write(daos)
        files.each { 
            it.write(daos)
        }
        
        daos.close()
        baos.toByteArray()
    }
    
    public void write(OutputStream os) {
        if (payload == null) {
            def baos = new ByteArrayOutputStream()
            def daos = new DataOutputStream(baos)
            
            daos.write(signablePayload())
            daos.write(sig)
            
            daos.close()
            payload = baos.toByteArray()
        }
        os.write(payload)
    }
    
    static class PathTree {
        
        private final Map<PathNode.Key, PathNode> keyToNode = new HashMap<>()
        
        final PathNode root
        PathTree(String root) {
            this.root = new PathNode(root, null)
            keyToNode.put(this.root.key(), this.root)
        }
        
        void add(List<String> paths) {
            PathNode current = null
            for (String path : paths) {
                if (current == null) {
                    if (path == root.path) {
                        current = root
                        continue
                    }
                }
                
                
                PathNode child = new PathNode(path, current)
                PathNode.Key childKey = child.key()
                if (!keyToNode.containsKey(childKey)) {
                    keyToNode.put(childKey, child)
                    current.children.add(child)
                }
                current = keyToNode.get(childKey)
            }
        }
    }
    
    static class PathNode {
        final String path
        final PathNode parent
        final Set<PathNode> children = new LinkedHashSet<>()
        private final int hashCode
        
        PathNode(String path, PathNode parent) {
            this.parent = parent
            this.path = path
            this.hashCode = Objects.hash(path, parent)
        }
        

        Key key() {
            return new Key()
        }
        
        private class Key {
            
            private final PathNode node = PathNode.this
            
            public int hashCode() {
                hashCode
            }
            public boolean equals(Object o) {
                Key other = (Key)o
                Objects.equals(path, other.node.path) &&
                    Objects.equals(parent, other.node.parent)
            }
        }         
    }
}