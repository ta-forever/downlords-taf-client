package com.faforever.client.galacticwar;

import com.brunomnsilva.smartgraph.graph.Graph;
import com.brunomnsilva.smartgraph.graph.GraphEdgeList;
import com.brunomnsilva.smartgraph.graph.Vertex;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import com.brunomnsilva.smartgraph.graphview.SmartGraphProperties;
import com.brunomnsilva.smartgraph.graphview.SmartGraphVertex;
import com.brunomnsilva.smartgraph.graphview.SmartGraphVertexNode;
import com.brunomnsilva.smartgraph.graphview.UtilitiesJavaFX;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Faction;
import com.faforever.client.theme.UiService;
import com.faforever.client.util.Tuple;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GalacticMapView {

  private static final double CAPITAL_PLANET_RADIUS = 30.0;
  private static final double PLANET_RADIUS = 20.0;
  private static final double ZOOM_CHANGE_MULTIPLIER = 1.05;
  private static final double WARNING_DOMINANCE_RATIO = 3.0;
  private static final String smartGraphProperties = """
    vertex.allow-user-move = false
    vertex.radius = 15
    vertex.tooltip = true
    vertex.label = true
    edge.tooltip = true
    edge.label = false
    edge.arrow = false
    layout.repulsive-force = 10000
    layout.attraction-force = 30
    layout.attraction-scale = 10""";


  private final UiService uiService;
  private final com.brunomnsilva.smartgraph.graph.Graph<Planet, String> theGraph;
  private double zoomFactor;
  private double xOffset;
  private double yOffset;
  private Map<Vertex<Planet>, SmartGraphVertexNode<Planet>> vertexNodes;
  SmartGraphPanel<Planet, String> smartGraphPanel;

  Consumer<Optional<Planet>> mousePressedHook;
  Consumer<Optional<Planet>> mouseReleasedHook;

  private GalacticMapView (
    Graph<Planet, String> theGraph, SmartGraphProperties properties, URI styleSheetUri, UiService uiService) {

    this.smartGraphPanel = new SmartGraphPanel<>(theGraph, properties, null, styleSheetUri);
    this.smartGraphPanel.setAutomaticLayout(false);

    this.uiService = uiService;
    this.theGraph = theGraph;
    this.zoomFactor = 1.0;
    this.xOffset = 0.0;
    this.yOffset = 0.0;

    accessVertexNodes();
    addCapitalIcons();
    addScoreIcons();
    setVertexStyles();
    setPlanetSizes();
    addLayoutListener();

    new java.util.Timer().schedule(new java.util.TimerTask() {
          @Override
          public void run() {
            JavaFxUtil.runLater(() -> {
              smartGraphPanel.init();
              setPlanetPositions();
            });
          }
        },
        300
    );

    getRoot().setOnScroll(event -> {
      adjustZoom(-event.getDeltaY(), event.getX(), event.getY());
      setPlanetSizes();
      setPlanetPositions();
    });

    final double[] mouseDragXYStart = new double[2];
    getRoot().setOnMouseDragged(event -> {
      double dx = (event.getX() - mouseDragXYStart[0]);
      double dy = (event.getY() - mouseDragXYStart[1]);
      mouseDragXYStart[0] = event.getX();
      mouseDragXYStart[1] = event.getY();
      if (event.isPrimaryButtonDown() || event.isMiddleButtonDown()) {
        xOffset += dx;
        yOffset += dy;
        setPlanetSizes();
        setPlanetPositions();
      }
      else if (event.isSecondaryButtonDown()) {
        adjustZoom(dy, event.getX(), event.getY());
        setPlanetSizes();
        setPlanetPositions();
      }
    });

    getRoot().setOnMousePressed(event -> {
      mouseDragXYStart[0] = event.getX();
      mouseDragXYStart[1] = event.getY();
      if (this.mousePressedHook != null) {
        if (event.getButton().equals(MouseButton.PRIMARY)) {
          Node node = UtilitiesJavaFX.pick(smartGraphPanel, event.getSceneX(), event.getSceneY());
          if (node == null) {
            return;
          }

          if (node instanceof SmartGraphVertex) {
            SmartGraphVertex<Planet> v = (SmartGraphVertex<Planet>)node;
            mousePressedHook.accept(Optional.of(v.getUnderlyingVertex().element()));
          }
          else {
            mousePressedHook.accept(Optional.empty());
          }
        }
      }
    });

    getRoot().setOnMouseReleased(event -> {
      if (event.getButton().equals(MouseButton.PRIMARY)) {
        Node node = UtilitiesJavaFX.pick(smartGraphPanel, event.getSceneX(), event.getSceneY());
        if (node == null) {
          return;
        }

        if (node instanceof SmartGraphVertex) {
          SmartGraphVertex<Planet> v = (SmartGraphVertex<Planet>)node;
          mouseReleasedHook.accept(Optional.of(v.getUnderlyingVertex().element()));
        }
        else {
          mouseReleasedHook.accept(Optional.empty());
        }
      }
    });

    final Boolean[] viewAlreadyReset = new Boolean[1];
    viewAlreadyReset[0] = false;
    getRoot().layoutBoundsProperty().addListener((obs, oldValue, newValue) -> {
      if (!viewAlreadyReset[0]) {
        viewAlreadyReset[0] = this.resetView();
      }
    });
  }

  void adjustZoom(double delta, double x0, double y0) {
    double originalZoom = zoomFactor;
    if (delta > 0.0) {
      zoomFactor /= ZOOM_CHANGE_MULTIPLIER;
    } else if (delta < 0.0) {
      zoomFactor *= ZOOM_CHANGE_MULTIPLIER;
    }
    x0 -= getRoot().getWidth()/2.0;
    y0 -= getRoot().getHeight()/2.0;
    xOffset = zoomFactor * (xOffset - x0) / originalZoom + x0;
    yOffset = zoomFactor * (yOffset - y0) / originalZoom + y0;
  }

  Pane getRoot() {
    return smartGraphPanel;
  }

  public static GalacticMapView fromFile(String path, String styleSheetFile, UiService uiService) throws IOException, URISyntaxException {
    Scenario scenario = Scenario.fromFile(Path.of(path));
    com.brunomnsilva.smartgraph.graph.Graph<Planet, String> theGraph = toSmartGraph(scenario);

    InputStream propertiesInputStream = new ByteArrayInputStream(smartGraphProperties.getBytes());
    SmartGraphProperties properties = new SmartGraphProperties(propertiesInputStream);
    URI styleSheetUri = new URI(styleSheetFile);
    return new GalacticMapView(theGraph, properties, styleSheetUri, uiService);
  }

  public void setMouseClickConsumer(Consumer<Optional<Planet>> consumer) {
    smartGraphPanel.setOnMouseClicked((mouseEvent) -> {
      if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
        Node node = UtilitiesJavaFX.pick(smartGraphPanel, mouseEvent.getSceneX(), mouseEvent.getSceneY());
        if (node == null) {
          return;
        }

        if (node instanceof SmartGraphVertex) {
          SmartGraphVertex<Planet> v = (SmartGraphVertex<Planet>)node;
          consumer.accept(Optional.of(v.getUnderlyingVertex().element()));
        }
        else {
          consumer.accept(Optional.empty());
        }
      }
    });
  }

  public void setMousePressedConsumer(Consumer<Optional<Planet>> consumer) {
    this.mousePressedHook = consumer;
  }

  public void setMouseReleasedConsumer(Consumer<Optional<Planet>> consumer) {
    this.mouseReleasedHook = consumer;
  }

  public boolean resetView() {
    Tuple<Double, Double> span = getGalacticSpan(theGraph.vertices());
    double xZoom = getRoot().getWidth() / (span.getFirst() + 2.0*CAPITAL_PLANET_RADIUS);
    double yZoom = getRoot().getHeight() / (span.getSecond() + 2.0*CAPITAL_PLANET_RADIUS);
    if (xZoom > 0.0 && yZoom > 0.0) {
      this.zoomFactor = Math.min(xZoom, yZoom);
      this.xOffset = 0.0;
      this.yOffset = 0.0;
      this.setPlanetSizes();
      this.setPlanetPositions();
      return true;
    }
    else {
      return false;
    }
  }

  public void setSelected(Planet planet) {
    for (Vertex<Planet> v: theGraph.vertices()) {
      if (v.element() == planet) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("selectedVertex");
      }
      else {
        smartGraphPanel.getStylableVertex(v).removeStyleClass("selectedVertex");
        smartGraphPanel.getStylableVertex(v).removeStyleClass("selectedVertex");
      }
    }
  }

  public void setSelected(String planetName) {
    for (Vertex<Planet> v: theGraph.vertices()) {
      if (v.element().getName().equals(planetName)) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("selectedVertex");
      }
      else {
        smartGraphPanel.getStylableVertex(v).removeStyleClass("selectedVertex");
        smartGraphPanel.getStylableVertex(v).removeStyleClass("selectedVertex");
      }
    }
  }

  public Optional<Planet> getPlanetByName(String name) {
    for (Vertex<Planet> v: theGraph.vertices()) {
      if (v.element().getName().equals(name)) {
        return Optional.of(v.element());
      }
    }
    return Optional.empty();
  }

  private static com.brunomnsilva.smartgraph.graph.Graph<Planet, String> toSmartGraph(Scenario scenario) {
    Map<Integer, Planet> planetsById = scenario.getPlanets().stream()
        .collect(Collectors.toMap(Planet::getId, Function.identity()));

    Graph<Planet, String> theGraph = new GraphEdgeList<>();
    for (Planet planet : scenario.getPlanets()) {
      theGraph.insertVertex(planet);
    }
    for (JumpGate jumpGate : scenario.getJumpGates()) {
      theGraph.insertEdge(
          planetsById.get(jumpGate.getSource()),
          planetsById.get(jumpGate.getTarget()),
          String.format("%s-%s", jumpGate.getSource(), jumpGate.getTarget())
      );
    }
    return theGraph;
  }

  private void accessVertexNodes() {
    try {
      Field vertexNodeField = smartGraphPanel.getClass().getDeclaredField("vertexNodes");
      vertexNodeField.setAccessible(true);
      vertexNodes = (Map<Vertex<Planet>, SmartGraphVertexNode<Planet>>) vertexNodeField.get(smartGraphPanel);
    } catch (NoSuchFieldException e) {
      log.error("[GalacticMapView] unable to retrieve vertexNodes from SmartGraphPanel NoSuchFieldException: {}", e.getMessage());
    } catch (IllegalAccessException e) {
      log.error("[GalacticMapView] unable to retrieve vertexNodes from SmartGraphPanel IllegalAccessException: {}", e.getMessage());
    }
  }

  private void setVertexStyles() {
    for (Vertex<Planet> v: theGraph.vertices()) {
      Faction owner = v.element().getControlledBy();
      if (owner == null) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("contestedVertex");
      } else if (owner == Faction.ARM) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("armVertex");
      } else if (owner == Faction.CORE) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("coreVertex");
      } else if (owner == Faction.GOK) {
        smartGraphPanel.getStylableVertex(v).addStyleClass("gokVertex");
      }
    }
  }

  private void addCapitalIcons() {
    for (Vertex<Planet> v: theGraph.vertices()) {
      Faction faction = v.element().getCapitalOf();
      if (faction != null) {
        Image im;
        if (faction == Faction.ARM) {
          im = uiService.getThemeImage(UiService.ARM_ICON_IMAGE_SMALL);
        } else if (faction == Faction.CORE) {
          im = uiService.getThemeImage(UiService.CORE_ICON_IMAGE_SMALL);
        } else {
          im = uiService.getThemeImage(UiService.GOK_ICON_IMAGE_SMALL);
        }
        ImageView imv = new ImageView(im);
        imv.setFitHeight(40.0);
        imv.setFitWidth(imv.getFitHeight() * 320.0 / 244.0);
        imv.setSmooth(true);
        imv.setMouseTransparent(true);
        vertexNodes.get(v).layoutBoundsProperty().addListener((obs, oldValue, newValue) -> {
          imv.setLayoutX(newValue.getCenterX() - imv.getFitWidth()/2.0);
          imv.setLayoutY(newValue.getCenterY() - imv.getFitHeight()/2.0);
        });
        smartGraphPanel.getStylableVertex(v).addStyleClass(String.format("%sCapitalVertex", faction.toString().toLowerCase()));
        smartGraphPanel.getChildren().add(imv);
      }
    }
  }

  private void addScoreIcons() {
    for (Vertex<Planet> v: theGraph.vertices()) {
      if (v.element().getControlledBy() == null) {
        Faction winning =  Collections.max(v.element().getScore().entrySet(),
            Comparator.comparingDouble(Map.Entry::getValue)).getKey();
        Faction losing =  Collections.min(v.element().getScore().entrySet(),
            Comparator.comparingDouble(Map.Entry::getValue)).getKey();
        if (v.element().getScore().get(winning) > WARNING_DOMINANCE_RATIO * v.element().getScore().get(losing) ||
            v.element().getSize() > 10.0 * v.element().getScore().get(losing)) {
          Label label = new Label("!");
          label.setStyle("-fx-font: 14 arial;");
          Region iconRegion = new Region();
          label.setGraphic(iconRegion);
          iconRegion.getStyleClass().add(UiService.CSS_CLASS_ICON);
          switch (winning) {
            case CORE -> {
              iconRegion.getStyleClass().add(UiService.CORE_STYLE_CLASS);
              label.setTextFill(Color.web("red"));
            }
            case GOK -> {
              iconRegion.getStyleClass().add(UiService.GOK_STYLE_CLASS);
              label.setTextFill(Color.web("green"));
            }
            case ARM -> {
              iconRegion.getStyleClass().add(UiService.ARM_STYLE_CLASS);
              label.setTextFill(Color.web("blue"));
            }
          }
          vertexNodes.get(v).layoutBoundsProperty().addListener((obs, oldValue, newValue) -> {
            label.setLayoutX(newValue.getCenterX() - iconRegion.getWidth()/2.0);
            label.setLayoutY(newValue.getCenterY() - iconRegion.getHeight()/2.0);
          });
          label.setMouseTransparent(true);
          smartGraphPanel.getChildren().add(label);
        }
      }
    }
  }

  private void addLayoutListener() {
    smartGraphPanel.layoutBoundsProperty().addListener((obs, oldValue, newValue) -> setPlanetPositions());
  }

  private Tuple<Double,Double> getGalacticCentre(Collection<Vertex<Planet>> planets) {
    if (planets.isEmpty()) {
      return new Tuple<>(0.0, 0.0);
    }
    else {
      Optional<Double> xMax = planets.stream()
          .map(v -> v.element().getGraphics().getX())
          .max(Double::compare);
      Optional<Double> xMin = planets.stream()
          .map(v -> v.element().getGraphics().getX())
          .min(Double::compare);
      Optional<Double> yMax = planets.stream()
          .map(v -> v.element().getGraphics().getY())
          .max(Double::compare);
      Optional<Double> yMin = planets.stream()
          .map(v -> v.element().getGraphics().getY())
          .min(Double::compare);
      if (xMin.isPresent() && yMax.isPresent() && yMin.isPresent()) {
        return new Tuple<>(0.5*xMax.get() +0.5*xMin.get(), 0.5*yMax.get() + 0.5*yMin.get());
      }
      else {
        return new Tuple<>(0.0, 0.0);
      }
    }
  }

  private Tuple<Double,Double> getGalacticSpan(Collection<Vertex<Planet>> planets) {
    if (planets.isEmpty()) {
      return new Tuple<>(100.0, 100.0);
    }
    else {
      Optional<Double> xMax = planets.stream()
          .map(v -> v.element().getGraphics().getX())
          .max(Double::compare);
      Optional<Double> xMin = planets.stream()
          .map(v -> v.element().getGraphics().getX())
          .min(Double::compare);
      Optional<Double> yMax = planets.stream()
          .map(v -> v.element().getGraphics().getY())
          .max(Double::compare);
      Optional<Double> yMin = planets.stream()
          .map(v -> v.element().getGraphics().getY())
          .min(Double::compare);
      if (xMin.isPresent() && yMax.isPresent() && yMin.isPresent()) {
        return new Tuple<>(xMax.get() - xMin.get(), yMax.get() - yMin.get());
      }
      else {
        return new Tuple<>(0.0, 0.0);
      }
    }
  }

  private void setPlanetSizes() {
    for (Vertex<Planet> v: theGraph.vertices()) {
      vertexNodes.get(v).setRadius(v.element().getCapitalOf() != null
          ? CAPITAL_PLANET_RADIUS * zoomFactor
          : PLANET_RADIUS * zoomFactor);
    }
  }

  private void setPlanetPositions() {
    Tuple<Double, Double> centroid = getGalacticCentre(theGraph.vertices());
    theGraph.vertices().forEach(v -> vertexNodes.get(v).setPosition(
        xOffset + (v.element().getGraphics().getX() - centroid.getFirst()) * zoomFactor + smartGraphPanel.getWidth()/2.0,
        yOffset + (v.element().getGraphics().getY() - centroid.getSecond()) * zoomFactor + smartGraphPanel.getHeight()/2.0));
  }
}
