package com.intellij.pom.tree.events.impl;

import com.intellij.pom.PomModelAspect;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.util.CharTable;
import com.intellij.lang.ASTNode;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Oct 6, 2004
 * Time: 11:11:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class TreeChangeEventImpl implements TreeChangeEvent{
  private final Map<ASTNode, TreeChange> myChangedElements = new HashMap<ASTNode, TreeChange>();
  private final List<ASTNode> myChangedInOrder = new ArrayList<ASTNode>();
  private final List<Set<ASTNode>> myOfEqualDepth = new ArrayList<Set<ASTNode>>(10);
  private final PomModelAspect myAspect;
  private final FileElement myFileElement;

  public TreeChangeEventImpl(PomModelAspect aspect, FileElement treeElement) {
    myAspect = aspect;
    myFileElement = treeElement;
  }

  public FileElement getRootElement() {
    return myFileElement;
  }

  public TreeElement[] getChangedElements() {
    final TreeElement[] ret = new TreeElement[myChangedElements.size()];
    final Iterator<ASTNode> iterator = myChangedInOrder.iterator();
    int index = 0;
    while (iterator.hasNext()) {
      ret[index++] = (TreeElement)iterator.next();
    }
    return ret;
  }

  public TreeChange getChangesByElement(ASTNode element) {
    return myChangedElements.get(element);
  }

  public void addElementaryChange(ASTNode element, ChangeInfo change) {
    int depth = 0;
    final ASTNode parent = element.getTreeParent();
    if(parent == null) return;
    ASTNode currentParent = parent;
    ASTNode prevParent = element;
    while(currentParent != null){
      if(myChangedElements.containsKey(currentParent)){
        final TreeChange changesByElement = getChangesByElement(currentParent);
        final boolean currentParentHasChange = changesByElement.getChangeByChild(prevParent) != null;
        if(currentParentHasChange && prevParent != element) return;
        if(prevParent != element){
          final ChangeInfo newChange = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, prevParent, myFileElement.getCharTable());
          if(change.getChangeType() != ChangeInfo.REMOVED)
            ((ChangeInfoImpl)newChange).processElementaryChange(currentParent, change, element);
          change = newChange;
        }
        processElementaryChange(currentParent, prevParent, change, -1);
        return;
      }
      depth++;
      prevParent = currentParent;
      currentParent = currentParent.getTreeParent();
    }
    compactChanges(parent, depth - 1);
    processElementaryChange(parent, element, change, depth - 1);
  }

  private int getDepth(ASTNode element) {
    int depth = 0;
    while((element = element.getTreeParent()) != null) depth++;
    return depth;
  }

  public void clear() {
    myChangedElements.clear();
    myOfEqualDepth.clear();
  }

  private void processElementaryChange(ASTNode parent, ASTNode element, ChangeInfo change, int depth) {
    TreeChange treeChange = myChangedElements.get(parent);
    if(treeChange == null){
      treeChange = new TreeChangeImpl(parent);
      myChangedElements.put(parent, treeChange);
      insertAtList(parent);

      final int index = depth >= 0 ? depth : getDepth(parent);
      Set<ASTNode> treeElements = index < myOfEqualDepth.size() ? myOfEqualDepth.get(index) : null;
      if(treeElements == null){
        treeElements = new HashSet<ASTNode>();
        while (index > myOfEqualDepth.size()) {
          myOfEqualDepth.add(new HashSet<ASTNode>());
        }
        myOfEqualDepth.add(index, treeElements);
      }
      treeElements.add(parent);
    }
    treeChange.addChange(element, change);
    if(change.getChangeType() == ChangeInfo.REMOVED){
      element.putUserData(CharTable.CHAR_TABLE_KEY, myFileElement.getCharTable());
    }
    if(treeChange.isEmpty()) removeAssociatedChanges(parent, depth);
  }

  private void compactChanges(ASTNode parent, int depth) {
    int currentDepth = myOfEqualDepth.size();
    while(--currentDepth > depth){
      final Set<ASTNode> treeElements = myOfEqualDepth.get(currentDepth);
      if(treeElements == null) continue;
      final Iterator<ASTNode> iterator = treeElements.iterator();
      while (iterator.hasNext()) {
        boolean isUnderCompacted = false;
        final TreeElement treeElement = (TreeElement)iterator.next();
        ASTNode currentParent = treeElement;
        while(currentParent != null){
          if(currentParent == parent){
            isUnderCompacted = true;
            break;
          }
          currentParent = currentParent.getTreeParent();
        }

        if(isUnderCompacted){
          final ChangeInfoImpl compactedChange = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, treeElement, myFileElement.getCharTable());
          compactedChange.compactChange(treeElement, getChangesByElement(treeElement));

          iterator.remove();
          removeAssociatedChanges(treeElement, currentDepth);
          final CompositeElement treeParent = treeElement.getTreeParent();
          final TreeChange changesByElement = getChangesByElement(treeParent);
          if (changesByElement != null) {
            final ChangeInfoImpl changeByChild = (ChangeInfoImpl)changesByElement.getChangeByChild(treeElement);
            if (changeByChild != null) {
              changeByChild.setOldLength(compactedChange.getOldLength());
            }
            else {
              changesByElement.addChange(treeElement, compactedChange);
            }
          }
          else {
            processElementaryChange(treeParent, treeElement, compactedChange, currentDepth - 1);
          }
        }
      }
    }
  }

  private void removeAssociatedChanges(ASTNode treeElement, int depth) {
    if(myChangedElements.remove(treeElement) != null) {
      myChangedInOrder.remove(treeElement);
      if (depth < 0) depth = getDepth(treeElement);
      if (depth < myOfEqualDepth.size()) {
        myOfEqualDepth.get(depth < 0 ? getDepth(treeElement) : depth).remove(treeElement);
      }
    }
  }

  private void insertAtList(ASTNode node){
    final int[] nodeRoute = getRoute(node);
    int index = 0;
    while(index < myChangedInOrder.size()){
      final ASTNode current = myChangedInOrder.get(index++);
      final int[] route = getRoute(current);
      if(compareRouts(nodeRoute, route) < 0) {
        myChangedInOrder.add(index, node);
        return;
      }
    }
    myChangedInOrder.add(node);
  }

  private int[] getRoute(ASTNode node){
    final List<ASTNode> parents = new ArrayList<ASTNode>(20);
    while(node != null){
      parents.add(node);
      node = node.getTreeParent();
    }
    final int[] root = new int[parents.size() - 1];
    for(int i = 0; i < root.length; i++){
      final ASTNode parent = parents.get(root.length - i - 1);
      int rootIndex = 0;
      ASTNode current = parent.getTreeParent().getFirstChildNode();
      while(current != parent){
        current = current.getTreeNext();
        rootIndex++;
      }
      root[i] = rootIndex;
    }
    return root;
  }

  private int compareRouts(int[] root1, int[] root2){
    final int depth = Math.min(root1.length, root2.length);
    for(int i = 0; i < depth; i++){
      if(root1[i] == root2[i]) continue;
      if(root1[i] > root2[i]){
        return 1;
      }
      else if(root2[i] > root1[i]){
        return -1;
      }
    }
    if(root1.length == root2.length) return 0;
    if(root1.length < root2.length) return 1;
    return -1;
  }

  public PomModelAspect getAspect() {
    return myAspect;
  }

  public String toString(){
    final StringBuffer buffer = new StringBuffer();
    final Iterator<Map.Entry<ASTNode, TreeChange>> iterator = myChangedElements.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<ASTNode, TreeChange> entry = iterator.next();
      buffer.append(entry.getKey().getElementType().toString());
      buffer.append(": ");
      buffer.append(entry.getValue().toString());
      buffer.append("\n");
    }
    return buffer.toString();
  }
}
