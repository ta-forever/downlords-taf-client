package com.faforever.client.main;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.config.ClientProperties.DiscordServer;
import com.faforever.client.discord.JoinDiscordEvent;
import com.faforever.client.fx.Controller;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class DiscordSelectionMenuController implements Controller<ContextMenu> {

  private final ClientProperties clientProperties;
  private final ApplicationEventPublisher applicationEventPublisher;
  public ContextMenu discordSelectionContextMenu;
  public List<MenuItem> menuItems;

  public DiscordSelectionMenuController(ClientProperties clientProperties, ApplicationEventPublisher applicationEventPublisher) {
    this.clientProperties = clientProperties;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public void initialize() {
    menuItems = new ArrayList<MenuItem>();
    List<DiscordServer> discordServers = this.clientProperties.getDiscord().getServers();
    Collections.shuffle(discordServers);
    for (DiscordServer server: discordServers) {
      MenuItem menuItem = new MenuItem(server.getTitle());
      menuItem.setOnAction((ActionEvent event) -> onSelectServer(server.getUrl()));
      this.getContextMenu().getItems().add(menuItem);
    }
  }

  public void onSelectServer(String url) {
    applicationEventPublisher.publishEvent(new JoinDiscordEvent(url));
  }

  ContextMenu getContextMenu() {
    return discordSelectionContextMenu;
  }

  @Override
  public ContextMenu getRoot() {
    return discordSelectionContextMenu;
  }

  public void consumer(ActionEvent actionEvent) {
    actionEvent.consume();
  }
}
