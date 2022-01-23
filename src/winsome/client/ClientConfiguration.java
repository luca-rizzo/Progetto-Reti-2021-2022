package winsome.client;

public class ClientConfiguration {
    //indirizzo IP del winsome.server
    private String serverIP;

    //porta su cui si mette ad ascoltare listen socket
    private int listenSocketPort;

    //porta su cui è in ascolto il registry
    private int serverRegistryPort;

    //nome di binding per l'oggetto RMI che serve ai winsome.client per registrarsi
    private String RMIObjectbindingName;

    //nome del dispositivo di interfaccia di rete collegato alla rete
    private String nameOfNetworkInterfaceDevice;

    public ClientConfiguration(String serverIP, int listenSocketPort, int serverRegistryPort, String RMIObjectbindingName, String nameOfConnectedNetworkInterface) {
        this.serverIP = serverIP;
        this.listenSocketPort = listenSocketPort;
        this.serverRegistryPort = serverRegistryPort;
        this.RMIObjectbindingName = RMIObjectbindingName;
        this.nameOfNetworkInterfaceDevice = nameOfConnectedNetworkInterface;
    }

    public ClientConfiguration() {
    }
    //*****GETTERS AND SETTERS*****//
    public String getNameOfNetworkInterfaceDevice() {
        return nameOfNetworkInterfaceDevice;
    }

    public void setNameOfNetworkInterfaceDevice(String nameOfNetworkInterfaceDevice) {
        this.nameOfNetworkInterfaceDevice = nameOfNetworkInterfaceDevice;
    }

    public String getServerIP() {
        return serverIP;
    }

    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }

    public int getListenSocketPort() {
        return listenSocketPort;
    }

    public void setListenSocketPort(int listenSocketPort) {
        this.listenSocketPort = listenSocketPort;
    }

    public int getServerRegistryPort() {
        return serverRegistryPort;
    }

    public void setServerRegistryPort(int serverRegistryPort) {
        this.serverRegistryPort = serverRegistryPort;
    }

    public String getRMIObjectbindingName() {
        return RMIObjectbindingName;
    }

    public void setRMIObjectbindingName(String RMIObjectbindingName) {
        this.RMIObjectbindingName = RMIObjectbindingName;
    }
}
