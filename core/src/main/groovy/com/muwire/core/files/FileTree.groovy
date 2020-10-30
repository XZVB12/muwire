package com.muwire.core.files

import java.util.concurrent.ConcurrentHashMap

class FileTree<T> {
    
    private final TreeNode root = new TreeNode()
    private final Map<File, TreeNode> fileToNode = new ConcurrentHashMap<>()
    
    synchronized void add(File file, T value) {
        List<File> path = new ArrayList<>()
        path.add(file)
        while (file.getParentFile() != null) {
            path.add(file.getParentFile())
            file = file.getParentFile()
        }
        
        Collections.reverse(path)
        
        TreeNode current = root
        for (File element : path) {
            TreeNode existing = fileToNode.get(element)
            if (existing == null) {
                existing = new TreeNode()
                existing.file = element
                existing.isFile = element.isFile()
                existing.parent = current
                fileToNode.put(element, existing)
                current.children.add(existing)
            }
            current = existing
        }
        current.value = value;
    }

    synchronized boolean remove(File file) {
        TreeNode node = fileToNode.remove(file)
        if (node == null) {
            return false
        }
        node.parent.children.remove(node)
        if (node.parent.children.isEmpty() && node.parent != root)
            remove(node.parent.file)
        def copy = new ArrayList(node.children)
        for (TreeNode child : copy)
            remove(child.file)
        true
    }    
    
    synchronized void traverse(FileTreeCallback<T> callback) {
        doTraverse(root, callback);
    }

    synchronized void traverse(File from, FileTreeCallback<T> callback) {
        if (from == null) {
            doTraverse(root, callback);
        } else {
            TreeNode node = fileToNode.get(from);
            if (node == null)
                return
            doTraverse(node, callback);
        }
    }
        
    private void doTraverse(TreeNode<T> node, FileTreeCallback<T> callback) {
        boolean leave = false
        if (node.file != null) {
            if (node.isFile)
                callback.onFile(node.file, node.value)
            else {
                leave = true
                callback.onDirectoryEnter(node.file)
            }
        }
        
        node.children.each { 
            doTraverse(it, callback)
        }        
        
        if (leave)
            callback.onDirectoryLeave()
    }
    
    synchronized void list(File parent, FileListCallback<T> callback) {
        TreeNode<T> node
        if (parent == null)
            node = root
        else 
            node = fileToNode.get(parent)
            
        node.children.each { 
            if (it.isFile)
                callback.onFile(it.file, it.value)
            else
                callback.onDirectory(it.file)
        }
    }
    
    synchronized File commonAncestor() {
        TreeNode current = root
        while(current.children.size() == 1)
            current = current.children.first()
        current.file
    }
    
    public static class TreeNode<T> {
        TreeNode parent
        File file
        boolean isFile
        T value;
        final Set<TreeNode> children = new HashSet<>()
        
        public int hashCode() {
            Objects.hash(file)
        }
        
        public boolean equals(Object o) {
            if (!(o instanceof TreeNode))
                return false
            TreeNode other = (TreeNode)o
            file == other.file
        }
    }
}
