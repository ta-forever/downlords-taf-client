package com.faforever.client.teammatchmaking;

import com.faforever.client.chat.ChatMessage;
import com.faforever.client.chat.CountryFlagService;
import com.faforever.client.chat.MatchmakingChatController;
import com.faforever.client.chat.avatar.AvatarService;
import com.faforever.client.chat.event.ChatMessageEvent;
import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.Faction;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.teammatchmaking.Party.PartyMember;
import com.faforever.client.theme.UiService;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.VisibleForTesting;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.faforever.client.chat.ChatService.PARTY_CHANNEL_SUFFIX;
import static java.lang.Math.ceil;
import static java.lang.Math.sqrt;
import static javafx.beans.binding.Bindings.createBooleanBinding;
import static javafx.beans.binding.Bindings.createObjectBinding;
import static javafx.beans.binding.Bindings.createStringBinding;

@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class TeamMatchmakingController extends AbstractViewController<Node> {

  private static final PseudoClass LEADER_PSEUDO_CLASS = PseudoClass.getPseudoClass("leader");
  private static final PseudoClass CHAT_AT_BOTTOM_PSEUDO_CLASS = PseudoClass.getPseudoClass("bottom");

  private final CountryFlagService countryFlagService;
  private final AvatarService avatarService;
  private final PreferencesService preferencesService;
  private final PlayerService playerService;
  private final I18n i18n;
  private final UiService uiService;
  private final TeamMatchmakingService teamMatchmakingService;
  private final FafService fafService;
  private final EventBus eventBus;

  public StackPane teamMatchmakingRoot;
  public Button invitePlayerButton;
  public Button leavePartyButton;
  public Label refreshingLabel;
  public ToggleButton armButton;
  public ToggleButton gokButton;
  public ToggleButton coreButton;
  public ImageView avatarImageView;
  public ImageView countryImageView;
  public Label clanLabel;
  public TextField usernameTextField;
  public Label gameCountLabel;
  public Label leagueLabel;
  public VBox queueBox;
  public GridPane partyMemberPane;
  public VBox preparationArea;
  public ImageView leagueImageView;
  public Label matchmakerHeadingLabel;
  public Label partyHeadingLabel;
  public Label queueHeadingLabel;
  public ScrollPane scrollPane;
  public HBox playerCard;
  public Label crownLabel;
  public TabPane chatTabPane;
  public GridPane contentPane;
  public ColumnConstraints column2;
  public RowConstraints row2;
  private Player player;
  private HashMap<Faction, ToggleButton> factionsToButtons;
  @VisibleForTesting
  protected MatchmakingChatController matchmakingChatController;

  private void initPlayer(Player newPlayer) {
    player = newPlayer;
    initializeUppercaseText();
    initializeBindings();
    ObservableList<Faction> factions = preferencesService.getPreferences().getMatchmaker().getFactions();
    selectFactions(factions);
    teamMatchmakingService.sendFactionSelection(factions);
    teamMatchmakingService.sendPlayerAlias(player.getAlias());
    teamMatchmakingService.getParty().getMembers().addListener((Observable o) -> renderPartyMembers());
    player.statusProperty().addListener((observable, oldValue, newValue) -> JavaFxUtil.runLater(() -> {
      if (newValue != PlayerStatus.IDLE) {
        teamMatchmakingService.getPlayersInGame().add(player);
      } else {
        teamMatchmakingService.getPlayersInGame().remove(player);
      }
    }));
    player.aliasProperty().addListener((observable, oldValue, newValue) -> new java.util.Timer().schedule(
        new java.util.TimerTask() {
          @Override
          public void run() {
            if (newValue.equals(player.getAlias())) {
              teamMatchmakingService.sendPlayerAlias(player.getAlias());
            }
          }
        }, 3000));
    fafService.requestMatchmakerInfo();
  }

  @Override
  public void initialize() {
    eventBus.register(this);
    JavaFxUtil.fixScrollSpeed(scrollPane);
    initializeDynamicChatPosition();

    factionsToButtons = new HashMap<>();
    factionsToButtons.put(Faction.CORE, coreButton);
    factionsToButtons.put(Faction.ARM, armButton);
    factionsToButtons.put(Faction.GOK, gokButton);

    playerService.currentPlayerProperty().addListener((obs,oldPlayer,newPlayer) -> initPlayer(newPlayer));
    if (playerService.getCurrentPlayer().isPresent()) {
      initPlayer(playerService.getCurrentPlayer().get());
    }

    if (teamMatchmakingService.isQueuesReadyForUpdate()) {
      renderQueues(); // The teamMatchmakingService may already have all queues collected
    }                 // so we won't get any updates on the following change listener
    teamMatchmakingService.queuesReadyForUpdateProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        renderQueues();
      }
    });

    teamMatchmakingService.getParty().getMembers().addListener((InvalidationListener) c -> {
      refreshingLabel.setVisible(false);
      selectFactionsBasedOnParty();
    });

    JavaFxUtil.addListener(teamMatchmakingService.getParty().ownerProperty(), (observable, oldValue, newValue) -> {
      if (matchmakingChatController != null) {
        matchmakingChatController.close();
      }
      createChannelTab("#" + newValue.getUsername() + PARTY_CHANNEL_SUFFIX);
    });
    try {
      createChannelTab("#" + teamMatchmakingService.getParty().getOwner().getUsername() + PARTY_CHANNEL_SUFFIX);
    }
    catch (NullPointerException e) { }
  }

  private void initializeDynamicChatPosition() {
    contentPane.widthProperty().addListener((observable, oldValue, newValue) -> {
      if ((double) newValue < 1115.0) {
        GridPane.setColumnIndex(chatTabPane, 0);
        GridPane.setRowIndex(chatTabPane, 1);
        GridPane.setColumnSpan(chatTabPane, 2);
        GridPane.setColumnSpan(scrollPane, 2);
        column2.setMinWidth(0);
        row2.setMinHeight(200);
        chatTabPane.pseudoClassStateChanged(CHAT_AT_BOTTOM_PSEUDO_CLASS, true);
      } else {
        GridPane.setColumnIndex(chatTabPane, 1);
        GridPane.setRowIndex(chatTabPane, 0);
        GridPane.setColumnSpan(chatTabPane, 1);
        GridPane.setColumnSpan(scrollPane, 1);
        column2.setMinWidth(400);
        row2.setMinHeight(0);
        chatTabPane.pseudoClassStateChanged(CHAT_AT_BOTTOM_PSEUDO_CLASS, false);
      }
    });
  }

  private void initializeUppercaseText() {
    matchmakerHeadingLabel.setText(i18n.get("teammatchmaking.playerTitle").toUpperCase());
    partyHeadingLabel.setText(i18n.get("teammatchmaking.partyTitle").toUpperCase());
    invitePlayerButton.setText(i18n.get("teammatchmaking.invitePlayer").toUpperCase());
    leavePartyButton.setText(i18n.get("teammatchmaking.leaveParty").toUpperCase());

    leagueLabel.textProperty().bind(createStringBinding(() -> i18n.get("leaderboard.divisionName").toUpperCase(),
        player.leaderboardRatingMapProperty())); // This should actually be a divisionProperty once that is available
    gameCountLabel.textProperty().bind(createStringBinding(() ->
        i18n.get("teammatchmaking.gameCount", player.getNumberOfGames()).toUpperCase(), player.numberOfGamesProperty()));
    queueHeadingLabel.textProperty().bind(createStringBinding(() -> {
      try {
        if (teamMatchmakingService.isCurrentlyInQueue())
          return i18n.get("teammatchmaking.queueTitle.inQueue").toUpperCase();
        else if (!teamMatchmakingService.getParty().getOwner().equals(player))
          return i18n.get("teammatchmaking.queueTitle.inParty").toUpperCase();
        else if (teamMatchmakingService.getPlayersInGame().contains(player))
          return i18n.get("teammatchmaking.queueTitle.inGame").toUpperCase();
        else if (!teamMatchmakingService.getPlayersInGame().isEmpty())
          return i18n.get("teammatchmaking.queueTitle.memberInGame").toUpperCase();
        else
          return i18n.get("teammatchmaking.queueTitle").toUpperCase();
      }
      catch (NullPointerException e) {
        return i18n.get("teammatchmaking.queueTitle").toUpperCase();
      }
    },  teamMatchmakingService.currentlyInQueueProperty(),
        teamMatchmakingService.getParty().ownerProperty(),
        teamMatchmakingService.getPlayersInGame()));
  }

  private void initializeBindings() {
    countryImageView.imageProperty().bind(createObjectBinding(() ->
        countryFlagService.loadCountryFlag(player.getCountry()).orElse(null), player.countryProperty()));
    avatarImageView.visibleProperty().bind(player.avatarUrlProperty().isNotNull().and(player.avatarUrlProperty().isNotEmpty()));
    avatarImageView.imageProperty().bind(createObjectBinding(() -> Strings.isNullOrEmpty(player.getAvatarUrl()) ? null : avatarService.loadAvatar(player.getAvatarUrl()), player.avatarUrlProperty()));
    leagueImageView.setManaged(false);
    JavaFxUtil.bindManagedToVisible(clanLabel, avatarImageView);

    clanLabel.visibleProperty().bind(player.clanProperty().isNotEmpty().and(player.clanProperty().isNotNull()));
    clanLabel.textProperty().bind(createStringBinding(() ->
        Strings.isNullOrEmpty(player.getClan()) ? "" : String.format("[%s]", player.getClan()), player.clanProperty()));
    usernameTextField.textProperty().bindBidirectional(player.aliasProperty());
    crownLabel.visibleProperty().bind(createBooleanBinding(() ->
            teamMatchmakingService.getParty().getMembers().size() > 1 && teamMatchmakingService.getParty().getOwner().equals(player),
        teamMatchmakingService.getParty().ownerProperty(), teamMatchmakingService.getParty().getMembers()));

    invitePlayerButton.disableProperty().bind(createBooleanBinding(
        () ->
            teamMatchmakingService.getParty().getOwner() == null ||
            teamMatchmakingService.getParty().getOwner().getId() != playerService.getCurrentPlayer().map(Player::getId).orElse(-1),
        teamMatchmakingService.getParty().ownerProperty(),
        playerService.currentPlayerProperty()
    ));
    leavePartyButton.disableProperty().bind(createBooleanBinding(() -> teamMatchmakingService.getParty().getMembers().size() <= 1, teamMatchmakingService.getParty().getMembers()));
  }

  private void renderPartyMembers() {
    playerCard.pseudoClassStateChanged(LEADER_PSEUDO_CLASS,
        (teamMatchmakingService.getParty().getOwner().equals(player) && teamMatchmakingService.getParty().getMembers().size() > 1));
    List<PartyMember> members = new ArrayList<>(teamMatchmakingService.getParty().getMembers());
    partyMemberPane.getChildren().clear();
    members.removeIf(partyMember -> partyMember.getPlayer().equals(player));
    for(int i = 0; i < members.size(); i++) {
      PartyMemberItemController controller = uiService.loadFxml("theme/play/teammatchmaking/matchmaking_member_card.fxml");
      controller.setMember(members.get(i));
      if (members.size() == 1) {
        partyMemberPane.add(controller.getRoot(), 0, 0, 2, 1);
      } else {
        partyMemberPane.add(controller.getRoot(), i % 2, i / 2);
      }
    }
  }

  @Override
  public Node getRoot() {
    return teamMatchmakingRoot;
  }

  public void onInvitePlayerButtonClicked() {
    InvitePlayerController invitePlayerController = uiService.loadFxml("theme/play/teammatchmaking/matchmaking_invite_player.fxml");
    Pane root = invitePlayerController.getRoot();
    uiService.showInDialog(teamMatchmakingRoot, root, i18n.get("teammatchmaking.invitePlayer"));
  }

  public void onLeavePartyButtonClicked() {
    teamMatchmakingService.leaveParty();
  }

  public void onFactionButtonClicked() {
    List<Faction> factions = factionsToButtons.entrySet().stream()
        .filter(entry -> entry.getValue().isSelected())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    if (factions.isEmpty()) {
      selectFactionsBasedOnParty();
      return;
    }
    preferencesService.getPreferences().getMatchmaker().getFactions().setAll(factions);
    preferencesService.storeInBackground();

    teamMatchmakingService.sendFactionSelection(factions);
    refreshingLabel.setVisible(true);
  }

  private void selectFactionsBasedOnParty() {
    List<Faction> factions = teamMatchmakingService.getParty().getMembers().stream()
        .filter(m -> m.getPlayer().getId() == player.getId())
        .findFirst()
        .map(PartyMember::getFactions)
        .orElse(List.of());
    selectFactions(factions);
  }

  private void selectFactions(List<Faction> factions) {
    factionsToButtons.forEach((faction, toggleButton) ->
        toggleButton.setSelected(factions.contains(faction)));
  }

  private void createChannelTab(String channelName) {
    matchmakingChatController = uiService.loadFxml("theme/play/teammatchmaking/matchmaking_chat.fxml");
    matchmakingChatController.setChannel(channelName);
    JavaFxUtil.runLater(() -> {
      chatTabPane.getTabs().clear();
      chatTabPane.getTabs().add(matchmakingChatController.getRoot());
    });
  }

  @Override
  protected void onDisplay(NavigateEvent navigateEvent) {
    if (matchmakingChatController != null) {
      matchmakingChatController.display(navigateEvent);
    }
  }

  @Subscribe
  public void onChatMessage(ChatMessageEvent event) {
    ChatMessage message = event.getMessage();
    if (message.getSource().equals(matchmakingChatController.getReceiver())) {
      JavaFxUtil.runLater(() -> matchmakingChatController.onChatMessage(message));
    }
  }

  private void renderQueues() {
    JavaFxUtil.runLater(() -> {
      List<MatchmakingQueue> queues = new ArrayList<>(teamMatchmakingService.getMatchmakingQueues());
      queueBox.getChildren().clear();
      queues.sort(Comparator.comparing(MatchmakingQueue::getQueueId));

      // work out how to arrange the buttons (MxN grid)
      int N = queues.size(), M = 1;
      if (queues.size() >= 6) {
        N = (int) ceil(sqrt(queues.size()));
        M = (int) ceil((float) queues.size() / (float) N);
      }

      // embed N buttons into M HBoxes embeded in 1 VBox
      Iterator<MatchmakingQueue> qit = queues.iterator();
      for (int m=0; m<M && qit.hasNext(); ++m) {
        HBox hbox = new HBox();
        hbox.setSpacing(12.0);
        queueBox.getChildren().add(hbox);
        for (int n=0; n<N && qit.hasNext(); ++n) {
          MatchmakingQueueItemController controller = uiService.loadFxml("theme/play/teammatchmaking/matchmaking_queue_card.fxml");
          controller.setQueue(qit.next());
          hbox.getChildren().add(controller.getRoot());
        }
      }
    });
  }
}
