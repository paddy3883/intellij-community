package com.intellij.packaging.impl.elements;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.artifacts.ChooseArtifactsDialog;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class ArtifactElementType extends PackagingElementType<ArtifactPackagingElement> {
  public static final ArtifactElementType ARTIFACT_ELEMENT_TYPE = new ArtifactElementType();

  ArtifactElementType() {
    super("artifact", "Artifact Output");
  }

  @Override
  public Icon getCreateElementIcon() {
    return ArtifactsStructureConfigurable.ARTIFACT_ICON;
  }

  @NotNull
  public List<? extends ArtifactPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                   CompositePackagingElement<?> parent) {
    ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(context.getProject(), getNotAddedArtifacts(context, artifact), "Choose Artifacts", "");
    dialog.show();
    final List<ArtifactPackagingElement> elements = new ArrayList<ArtifactPackagingElement>();
    if (dialog.isOK()) {
      for (Artifact selected : dialog.getChosenElements()) {
        elements.add(new ArtifactPackagingElement(selected.getName()));
      }
    }
    return elements;
  }

  @NotNull
  public static List<? extends Artifact> getNotAddedArtifacts(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact) {
    final Set<Artifact> result = new HashSet<Artifact>(Arrays.asList(context.getArtifactModel().getArtifacts()));
    ArtifactUtil.processPackagingElements(artifact, ARTIFACT_ELEMENT_TYPE, new Processor<ArtifactPackagingElement>() {
      public boolean process(ArtifactPackagingElement artifactPackagingElement) {
        result.remove(artifactPackagingElement.findArtifact(context));
        return true;
      }
    }, context, true);
    result.remove(artifact);
    return new ArrayList<Artifact>(result);
  }

  @NotNull
  public ArtifactPackagingElement createEmpty() {
    return new ArtifactPackagingElement();
  }
}
