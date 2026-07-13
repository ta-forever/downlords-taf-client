isScrolledToBottom = true;
window.onscroll = function (e) {
  isScrolledToBottom = window.scrollY + window.innerHeight + 20 >= document.documentElement.scrollHeight;
};

function link(input) {
  return Autolinker.link(input, {
        email: false,
        phone: false,
        twitter: false,
        replaceFn: function (autolinker, match) {
          return new Autolinker.HtmlTag({
            tagName: "a",
            attrs: {
              "href": "javascript:void(0);",
                "onClick": "java.openUrl('" + match.getUrl() + "')",
                "onMouseOver": "java.previewUrl('" + match.getUrl() + "')",
                "onMouseOut": "java.hideUrlPreview()"
            },
            innerHtml: match.getAnchorText()
          });
        }
      }
  );
}

function showClanInfo(clanTag) {
    java.clanInfo(clanTag);
}

function hideClanInfo() {
    java.hideClanInfo();
}

function showClanWebsite(clanTag) {
    java.showClanWebsite(clanTag);
}

function showPlayerInfo(playerName) {
    java.playerInfo(playerName);
}

function hidePlayerInfo() {
    java.hidePlayerInfo();
}

function messagePlayer(playerName) {
    java.openPrivateMessageTab(playerName);
}

function scrollToBottomIfDesired() {
  if (isScrolledToBottom) {
    window.scrollTo(0, document.documentElement.scrollHeight);
  }
}

function setAllMessageColors(userListString) {
  var userList = JSON.parse(userListString);

  for (var user in userList) {
    if (userList.hasOwnProperty(user)) {
      updateUserMessageColor(user, userList[user]);
    }
  }
}

function removeAllMessageColors() {
  var messages = document.getElementsByClassName("chat-message");
  for (var i = 0; i < messages.length; i++) {
    messages[i].style.color = "";
  }
}

function updateUserMessageColor(user, color) {
  var messages = document.getElementsByClassName("user-" + user);
  for (var i = 0; i < messages.length; i++) {
    messages[i].style.color = color;
  }
}

function addUserMessageClass(user, cssClass) {
  var userMessages = document.getElementsByClassName(user);
  for (var i = 0; i < userMessages.length; i++) {
    userMessages[i].classList.add(cssClass);
  }
}

function removeUserMessageClass(user, cssClass) {
    if (!cssClass) {
        return;
    }
  var userMessages = document.getElementsByClassName(user);
  for (var i = 0; i < userMessages.length; i++) {
    userMessages[i].classList.remove(cssClass);
  }
}

function clearUserMessageClasses(user) {
    var userMessages = document.getElementsByClassName(user);
    for (var i = 0; i < userMessages.length; i++) {
        userMessages[i].className = "";
    }
}

function updateUserMessageDisplay(user, display) {
  var userMessages = document.getElementsByClassName("chat-section-" + user);
  for (var i = 0; i < userMessages.length; i++) {
    userMessages[i].style.display = display;
  }
}

// Swap the avatar image for every already-rendered chat section of one user. Used when a chosen
// medal-as-avatar resolves after the message was rendered (cold cache) or changes live, so message
// avatars stay in sync with the chat user list. Pass the regular avatar url/title/"" to revert.
function updateUserAvatarMedal(user, avatarUrl, avatarTitle, avatarStyle) {
  var sections = document.getElementsByClassName("chat-section-" + user);
  for (var i = 0; i < sections.length; i++) {
    var avatars = sections[i].getElementsByClassName("avatar");
    for (var j = 0; j < avatars.length; j++) {
      avatars[j].src = avatarUrl;
      avatars[j].title = avatarTitle;
      avatars[j].setAttribute("style", avatarStyle);
    }
  }
}


function highlightText(text) {
  $('#chat-container').removeHighlight().highlight(text);
}

function removeHighlight() {
  $('#chat-container').removeHighlight();
}

function toggleTextVisibility(messageId) {
  const chatText = document.getElementById(`chat-text-${messageId}`);
  const textContent = chatText.querySelector('.text-content');
  const textRedacted = chatText.querySelector('.text-redacted');

  if (textContent.style.display === 'none') {
    textContent.style.display = 'block';
    textRedacted.style.display = 'none';
  } else {
    textContent.style.display = 'none';
    textRedacted.style.display = 'block';
  }
}

// Toggle visibility of the {toxicity} field for all text lines
function toggleToxicityVisibility() {
  const allToxicityFields = document.querySelectorAll('.toxicity');
  allToxicityFields.forEach(field => {
    field.style.display = field.style.display === 'none' ? 'inline' : 'none';
  });
}

function enableToxicityVisibility() {
  const allToxicityFields = document.querySelectorAll('.toxicity');
  allToxicityFields.forEach(field => {
    field.style.display = 'inline';
  });
}

function disableToxicityVisibility() {
  const allToxicityFields = document.querySelectorAll('.toxicity');
  allToxicityFields.forEach(field => {
    field.style.display = 'none';
  });
}

function revealText(messageId) {
  const textLine = document.getElementById(`text-line-${messageId}`);
  const text = textLine.querySelector('.text');
  const textRedacted = textLine.querySelector('.text-redacted');

  text.style.display = 'block';
  textRedacted.style.display = 'none';
}

function hideText(messageId) {
  const textLine = document.getElementById(`text-line-${messageId}`);
  const text = textLine.querySelector('.text');
  const textRedacted = textLine.querySelector('.text-redacted');

  text.style.display = 'none';
  textRedacted.style.display = 'block';
}
