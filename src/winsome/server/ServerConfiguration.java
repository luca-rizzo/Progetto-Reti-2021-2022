package winsome.server;

public class ServerConfiguration {
    //path del file di backup dello stato del winsome.server
    private String backupJsonFile;

    //indirizzo IP del winsome.server
    private String IP;

    //porta su cui si mette ad ascoltare listen socket
    private int TCPport;

    //indirizzo del gruppo multicast a cui i winsome.client devono iscriversi per ricevere aggiornamenti sul calcolo notifiche
    private String multicastAddress;

    //porta di multicast su cui winsome.client devono mettersi in ascolto per ricevere aggiornamenti sul calcolo notifiche
    private int multicastPort;

    //porta su cui è in ascolto il registry
    private int registryPort;

    //nome di binding per l'oggetto RMI che serve ai winsome.client per registrarsi
    private String RMIObjectbindingName;

    //ogni quanti minuti calcolare ricompense
    private int timerRewardMin;

    //ogni quanti minuti eseguire backup
    private int timerBackupMin;

    //numero di worker per la gestione delle richieste
    private int nWorker;

    //percentuale ricompensa autore
    private int authorPercentage;

    public ServerConfiguration(){

    }

    public ServerConfiguration(String backupJsonFile, String IP, int TCPport, String multicastAddress, int multicastPort, int registryPort, String RMIObjectbindingName, int timerRewardMin, int timerBackupMin, int nWorker, int authorPercentage) {
        this.backupJsonFile = backupJsonFile;
        this.IP = IP;
        this.TCPport = TCPport;
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.registryPort = registryPort;
        this.RMIObjectbindingName = RMIObjectbindingName;
        this.timerRewardMin = timerRewardMin;
        this.timerBackupMin = timerBackupMin;
        this.nWorker = nWorker;
        this.authorPercentage = authorPercentage;
    }


    //***** GETTERS AND SETTER *****//
    //MOLTI SONO PRESENTI IN QUANTO NECESSARI ALLA LIBRERIA JACKSON

    public String getBackupJsonFile() {
        return backupJsonFile;
    }

    public void setBackupJsonFile(String backupJsonFile) {
        this.backupJsonFile = backupJsonFile;
    }

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public int getTCPport() {
        return TCPport;
    }

    public void setTCPport(int TCPport) {
        this.TCPport = TCPport;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public void setMulticastAddress(String multicastAddress) {
        this.multicastAddress = multicastAddress;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    public void setMulticastPort(int multicastPort) {
        this.multicastPort = multicastPort;
    }

    public int getRegistryPort() {
        return registryPort;
    }

    public void setRegistryPort(int registryPort) {
        this.registryPort = registryPort;
    }

    public String getRMIObjectbindingName() {
        return RMIObjectbindingName;
    }

    public void setRMIObjectbindingName(String RMIObjectbindingName) {
        this.RMIObjectbindingName = RMIObjectbindingName;
    }

    public int getTimerRewardMin() {
        return timerRewardMin;
    }

    public void setTimerRewardMin(int timerRewardMin) {
        this.timerRewardMin = timerRewardMin;
    }

    public int getTimerBackupMin() {
        return timerBackupMin;
    }

    public void setTimerBackupMin(int timerBackupMin) {
        this.timerBackupMin = timerBackupMin;
    }

    public int getnWorker() {
        return nWorker;
    }

    public void setnWorker(int nWorker) {
        this.nWorker = nWorker;
    }

    public int getAuthorPercentage() {
        return authorPercentage;
    }

    public void setAuthorPercentage(int authorPercentage) {
        this.authorPercentage = authorPercentage;
    }
}
