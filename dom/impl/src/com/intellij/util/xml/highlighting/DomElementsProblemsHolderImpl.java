/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiLock;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl implements DomElementsProblemsHolder {
  private final Map<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>> myCachedErrors =
    new ConcurrentHashMap<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>();
  private final Map<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>> myCachedChildrenErrors =
    new ConcurrentHashMap<DomElement, Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>();
  private final List<Annotation> myAnnotations = new ArrayList<Annotation>();

  private final Function<DomElement, List<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, List<DomElementProblemDescriptor>>() {
      public List<DomElementProblemDescriptor> fun(final DomElement s) {
        final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> map = myCachedErrors.get(s);
        return map != null ? ContainerUtil.concat(map.values()) : Collections.<DomElementProblemDescriptor>emptyList();
      }
    };

  private final DomFileElement myElement;

  private static final Factory<Map<Class<? extends DomElementsInspection>,List<DomElementProblemDescriptor>>> CONCURRENT_HASH_MAP_FACTORY = new Factory<Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>>() {
    public Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> create() {
      return new ConcurrentHashMap<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>();
    }
  };
  private static final Factory<List<DomElementProblemDescriptor>> SMART_LIST_FACTORY = new Factory<List<DomElementProblemDescriptor>>() {
    public List<DomElementProblemDescriptor> create() {
      return new SmartList<DomElementProblemDescriptor>();
    }
  };
  private final Set<Class<? extends DomElementsInspection>> myPassedInspections = new THashSet<Class<? extends DomElementsInspection>>();

  public DomElementsProblemsHolderImpl(final DomFileElement element) {
    myElement = element;
  }

  public final void appendProblems(final DomElementAnnotationHolderImpl holder, final Class<? extends DomElementsInspection> inspectionClass) {
    if (isInspectionCompleted(inspectionClass)) return;

    for (final DomElementProblemDescriptor descriptor : holder) {
      addProblem(descriptor, inspectionClass);
    }
    myAnnotations.addAll(holder.getAnnotations());
    myPassedInspections.add(inspectionClass);
  }

  public final boolean isInspectionCompleted(@NotNull final DomElementsInspection inspection) {
    return isInspectionCompleted(inspection.getClass());
  }

  public final boolean isInspectionCompleted(final Class<? extends DomElementsInspection> inspectionClass) {
    synchronized (PsiLock.LOCK) {
      return myPassedInspections.contains(inspectionClass);
    }
  }

  public final List<Annotation> getAnnotations() {
    return myAnnotations;
  }

  public final void addProblem(final DomElementProblemDescriptor descriptor, final Class<? extends DomElementsInspection> inspection) {
    ContainerUtil.getOrCreate(ContainerUtil.getOrCreate(myCachedErrors, descriptor.getDomElement(), CONCURRENT_HASH_MAP_FACTORY), inspection,
                              SMART_LIST_FACTORY).add(descriptor);
  }

  @NotNull
  public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();
    return myDomProblemsGetter.fun(domElement);
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    return getProblems(domElement);
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren) {
    if (!withChildren || domElement == null || !domElement.isValid()) {
      return getProblems(domElement);
    }

    return ContainerUtil.concat(getProblemsMap(domElement).values());
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       final HighlightSeverity minSeverity) {
    return getProblems(domElement, withChildren, minSeverity);
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement, final boolean withChildren, final HighlightSeverity minSeverity) {
    return ContainerUtil.findAll(getProblems(domElement, true, withChildren), new Condition<DomElementProblemDescriptor>() {
      public boolean value(final DomElementProblemDescriptor object) {
        return object.getHighlightSeverity().compareTo(minSeverity) >= 0;
      }
    });

  }

  @NotNull
  private Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> getProblemsMap(final DomElement domElement) {
    final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> map = myCachedChildrenErrors.get(domElement);
    if (map != null) {
      return map;
    }

    final Map<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>> problems = new ConcurrentHashMap<Class<? extends DomElementsInspection>, List<DomElementProblemDescriptor>>();
    mergeMaps(problems, myCachedErrors.get(domElement));
    domElement.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        mergeMaps(problems, getProblemsMap(element));
      }
    });
    myCachedChildrenErrors.put(domElement, problems);
    return problems;
  }

  private static <T> void mergeMaps(final Map<T, List<DomElementProblemDescriptor>> accumulator, @Nullable final Map<T, List<DomElementProblemDescriptor>> toAdd) {
    if (toAdd == null) return;
    for (final Map.Entry<T, List<DomElementProblemDescriptor>> entry : toAdd.entrySet()) {
      ContainerUtil.getOrCreate(accumulator, entry.getKey(), SMART_LIST_FACTORY).addAll(entry.getValue());
    }
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return getProblems(myElement, false, true);
  }

  public List<DomElementProblemDescriptor> getAllProblems(@NotNull DomElementsInspection inspection) {
    if (!myElement.isValid()) {
      return Collections.emptyList();
    }
    final List<DomElementProblemDescriptor> list = getProblemsMap(myElement).get(inspection.getClass());
    return list != null ? new ArrayList<DomElementProblemDescriptor>(list) : Collections.<DomElementProblemDescriptor>emptyList();
  }
}
