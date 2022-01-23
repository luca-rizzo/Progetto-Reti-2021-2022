#!/bin/bash

export PATH_TO_FX=./lib/openjfx-17.0.1_linux-x64_bin-sdk/javafx-sdk-17.0.1/lib
#compilazione
javac --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -cp ./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar src/winsome/WinGUIClientMain.java src/winsome/client/GUI/*.java src/winsome/client/*.java src/winsome/jsonUtility/*.java src/winsome/resourseRappresentation/*.java src/winsome/RESTfulUtility/*.java src/winsome/server/*.java -d out/production 
#esecuzione
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -cp ./out/production:./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar winsome.WinGUIClientMain


