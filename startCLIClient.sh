#!/bin/bash

#compilazione
javac -cp ./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar src/winsome/WinCLIClientMain.java src/winsome/client/CLI/*.java src/winsome/client/*.java src/winsome/jsonUtility/*.java src/winsome/resourseRappresentation/*.java src/winsome/RESTfulUtility/*.java src/winsome/server/*.java -d out/production
#esecuzione
java -cp ./out/production:./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar winsome.WinCLIClientMain
