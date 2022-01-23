package winsome;

import winsome.client.CLI.CLIClient;

public class WinCLIClientMain {
    public static void main(String[] args){
        new Thread(new CLIClient("resource/configClient.json")).start();
    }
}
