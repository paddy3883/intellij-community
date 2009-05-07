package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.facet.impl.DefaultFacetsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@State(
    name = ArtifactManagerImpl.COMPONENT_NAME,
    storages = {
        @Storage(id = "other", file = "$PROJECT_FILE$"),
        @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/artifacts.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ArtifactManagerImpl extends ArtifactManager implements ProjectComponent, PersistentStateComponent<ArtifactManagerState> {
  @NonNls public static final String COMPONENT_NAME = "ArtifactManager";
  private final ArtifactManagerModel myModel = new ArtifactManagerModel();
  private final Project myProject;
  private final DefaultPackagingElementResolvingContext myResolvingContext;

  public ArtifactManagerImpl(Project project) {
    myProject = project;
    myResolvingContext = new DefaultPackagingElementResolvingContext(myProject);
  }

  @NotNull
  public Artifact[] getArtifacts() {
    return myModel.getArtifacts();
  }

  public Artifact findArtifact(@NotNull String name) {
    return myModel.findArtifact(name);
  }

  @NotNull
  public Artifact getModifiableOrOriginal(@NotNull Artifact artifact) {
    return myModel.getModifiableOrOriginal(artifact);
  }

  public ArtifactManagerState getState() {
    final ArtifactManagerState state = new ArtifactManagerState();
    for (Artifact artifact : getArtifacts()) {
      final ArtifactState artifactState = new ArtifactState();
      artifactState.setBuildOnMake(artifact.isBuildOnMake());
      artifactState.setName(artifact.getName());
      artifactState.setOutputPath(artifact.getOutputPath());
      artifactState.setRootElement(serializePackagingElement(artifact.getRootElement()));
      state.getArtifacts().add(artifactState);
    }
    return state;
  }

  private static Element serializePackagingElement(PackagingElement<?> packagingElement) {
    Element element = new Element("element");
    element.setAttribute("id", packagingElement.getType().getId());
    final Object bean = packagingElement.getState();
    if (bean != null) {
      XmlSerializer.serializeInto(bean, element);
    }
    if (packagingElement instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)packagingElement).getChildren()) {
        element.addContent(serializePackagingElement(child));
      }
    }
    return element;
  }

  public static <T> PackagingElement<T> deserializeElement(Element element) {
    final String id = element.getAttributeValue("id");
    PackagingElementType<?> type = PackagingElementFactory.getInstance().findElementType(id);
    PackagingElement<T> packagingElement = (PackagingElement<T>)type.createEmpty();
    T state = packagingElement.getState();
    if (state != null) {
      XmlSerializer.deserializeInto(state, element);
      packagingElement.loadState(state);
    }
    final List children = element.getChildren("element");
    //noinspection unchecked
    for (Element child : (List<? extends Element>)children) {
      ((CompositePackagingElement<?>)packagingElement).addChild(ArtifactManagerImpl.deserializeElement(child));
    }
    return packagingElement;
  }

  public void loadState(ArtifactManagerState managerState) {
    final List<ArtifactImpl> artifacts = new ArrayList<ArtifactImpl>();
    for (ArtifactState state : managerState.getArtifacts()) {
      final Element element = state.getRootElement();
      final ArtifactRootElementImpl rootElement;
      if (element != null) {
        rootElement = (ArtifactRootElementImpl)deserializeElement(element);
      }
      else {
        rootElement = new ArtifactRootElementImpl();
      }
      artifacts.add(new ArtifactImpl(state.getName(), state.isBuildOnMake(), rootElement, state.getOutputPath()));
    }
    commit(artifacts);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @Override
  public ModifiableArtifactModel createModifiableModel() {
    final ArtifactModelImpl model = new ArtifactModelImpl(this);
    model.addArtifacts(getArtifactsList());
    return model;
  }

  @Override
  public PackagingElementResolvingContext getResolvingContext() {
    return myResolvingContext;
  }

  public List<ArtifactImpl> getArtifactsList() {
    return myModel.myArtifactsList;
  }

  public void commit(List<ArtifactImpl> artifacts) {
    myModel.setArtifactsList(artifacts);
  }

  private static class ArtifactManagerModel extends ArtifactModelBase {
    private List<ArtifactImpl> myArtifactsList = new ArrayList<ArtifactImpl>();

    public void setArtifactsList(List<ArtifactImpl> artifactsList) {
      myArtifactsList = artifactsList;
      artifactsChanged();
    }

    protected List<? extends Artifact> getArtifactsList() {
      return myArtifactsList;
    }
  }

  private static class DefaultPackagingElementResolvingContext implements PackagingElementResolvingContext {
    private final Project myProject;
    private final DefaultModulesProvider myModulesProvider;

    public DefaultPackagingElementResolvingContext(Project project) {
      myProject = project;
      myModulesProvider = new DefaultModulesProvider(myProject);
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public ArtifactModel getArtifactModel() {
      return getInstance(myProject);
    }

    @NotNull
    public ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @NotNull
    public FacetsProvider getFacetsProvider() {
      return DefaultFacetsProvider.INSTANCE;
    }
  }
}
