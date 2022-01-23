package winsome;

import winsome.server.WinSomeServer;
import java.io.IOException;
import java.util.Scanner;

public class WinServerMain {
    public static void main(String[] args) {
        Thread winServerThread = null;
        try {
            //creo un nuovo thread per WinsomeServer a cui passo il file di configurazione del server
            winServerThread = new Thread(new WinSomeServer("resource/configServer.json"));
        } catch (IOException e) {
            //il file di configurazione non rispetta gli standard specificati nella relazione
            System.out.println("Errore nel parsing del file di configurazione!");
            System.exit(-1);
        }
        //avvio il thread
        winServerThread.start();

        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        while(!line.equals("exit")){
            //rimango in attesa di ricevere il comando exit per terminare WinsomeServer
            line = scanner.nextLine();
        }

        //interrompo il thread ed eseguo il join per terminare
        winServerThread.interrupt();
        try{
            winServerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
