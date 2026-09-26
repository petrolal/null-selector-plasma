import QtQuick 2.15
import QtQuick.Window 2.15
import QtQuick.Controls 2.15
import QtMultimedia
import "Components"

Item {
  id: root
  height: Screen.height
  width: Screen.width
  Rectangle {
    id: background
    anchors.fill: parent
    height: parent.height
    width: parent.width
    z: 0
    color: config.bgDefault
  }
  MediaPlayer {
    id: player
    source: config.VideoBackground ? config.VideoBackground : ""
    videoOutput: videoBg
    loops: MediaPlayer.Infinite
    audioOutput: AudioOutput {
      muted: true
    }
    Component.onCompleted: {
      if (config.VideoBackground && config.VideoBackground !== "") {
        player.play()
      }
    }
  }
  VideoOutput {
    id: videoBg
    anchors.fill: parent
    fillMode: VideoOutput.PreserveAspectCrop
    visible: config.VideoBackground && config.VideoBackground !== ""
    z: 1
  }
  Image {
    id: backgroundImage
    anchors.fill: parent
    height: parent.height
    width: parent.width
    fillMode: Image.PreserveAspectCrop
    visible: (!config.VideoBackground || config.VideoBackground === "") && config.CustomBackground == "true"
    z: 1
    source: config.Background
    asynchronous: false
    cache: true
    mipmap: true
    clip: true
  }
  Item {
    id: mainPanel
    z: 3
    anchors {
      fill: parent
      leftMargin: Screen.width * 0.02
      rightMargin: Screen.width * 0.02
      bottomMargin: Screen.height * 0.02
    }
    Clock {
      id: time
      visible: config.ClockEnabled == "true" ? true : false
    }
    LoginPanel {
      id: loginPanel
      anchors.fill: parent
    }
  }
}
