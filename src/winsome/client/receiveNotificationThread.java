package winsome.client;

import java.io.IOException;
import java.net.*;

public class receiveNotificationThread extends Thread{
    //socketAddress associata al gruppo multicast a cui devo iscrivermi
    private final InetSocketAddress multicastGroup;
    //multicast socket per la recezione dei messaggi di notifica inviati dal thread che esegue RewardCalculator nel winsomeServer
    private MulticastSocket ms;
    //network interface attraverso cui rcevere messaggi UDP di notifca
    private NetworkInterface networkInterface;
    //nome del dispositivo associato alla NetworkInterface
    private final String nameOfNetworkInterfaceDevice;

    public receiveNotificationThread(InetSocketAddress multicastGroup, String nameOfNetworkInterfaceDevice) {
        this.multicastGroup = multicastGroup;
        this.nameOfNetworkInterfaceDevice = nameOfNetworkInterfaceDevice;
    }

    @Override
    public void run() {
        try{
            this.ms = new MulticastSocket(multicastGroup.getPort());
            this.networkInterface = NetworkInterface.getByName(nameOfNetworkInterfaceDevice);
            if(networkInterface == null){
                System.out.println("ERROR: Network interface not found. Plis enter the name of your network interface in configClient.json to receive notification of reward from winsome.server");
                return;
            }
            //mi iscrivo al gruppo multicast
            ms.joinGroup(multicastGroup, networkInterface);
            System.out.println("Start to receive reward notification");
            //continuerò a leggere messaggi di aggiornamento fin quando non vengo interrotto
            while(!Thread.interrupted()){
                byte[] buffer = new byte[256];
                DatagramPacket receiveMessage = new DatagramPacket(buffer, buffer.length);
                ms.receive(receiveMessage);
                System.out.println(new String(receiveMessage.getData(),0,receiveMessage.getLength()));
            }
        } catch (SocketException e1) {
            System.out.println("Stop to receive reward notification");
        } catch (IOException e2){
            e2.printStackTrace();
        }
    }

    //metodo riscritto per poter svegliare il thread in lettura sulla multicast socket attraverso l'interrupt: questo solleverà un'eccezione nel
    //thread in attesa di ricevere aggiornamenti attraverso la ms.receive(receiveMessaage) che farà stampare un messaggio sul terminale che indica la terminazione del thread
    @Override
    public void interrupt(){
        super.interrupt();
        try {
            //abbandono il gruppo multicast
            ms.leaveGroup(multicastGroup, networkInterface);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.ms.close();
    }
}
