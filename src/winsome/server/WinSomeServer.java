package winsome.server;

import winsome.RESTfulUtility.Token;
import winsome.client.IntRMIClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.resourseRappresentation.Post;
import winsome.resourseRappresentation.Reaction;
import winsome.resourseRappresentation.User;
import winsome.resourseRappresentation.Wallet;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

public class WinSomeServer implements Runnable{
    //dimensione massima del buffer per la lettura delle richieste
    static private final int MAXDIMBUFFER = 30000;

    //threadPool per la gestione delle richieste e la preparazione delle risposte
    final private ExecutorService workersThreadPool;

    //threadPool per l'esecuzione periodica dei task RewardCalculator e DataBackupTask
    final private ScheduledExecutorService fixedTaskScheduler;

    //lista di SelectionKey pronte per essere registrate in scrittura: il thread dispatcher la scorrerà ad ogni iterazione
    //e registrerà per ciascuna chiave presente un interesse in scrittura
    final private  ArrayList<SelectionKey> readyToWrite;

    //contiene tutti i parametri di configurazione del server
    final private ServerConfiguration serverConfiguration;

    //mantiene le associazioni <username,oggetto_user_con_tale_username>: contiene tutti gli utenti registrati a WINSOME
    final private ConcurrentHashMap<String, User> registeredUsers;

    //mantiene le associazioni <Token,oggetto_user_con_tale_token>    : contiene tutti gli utenti al momento loggati su WINSOME
    final private ConcurrentHashMap<Token, User> loggedUsers;

    //mantiene le associazioni <username,oggettoCallBack_associato_a_tale_username>: contiene tutti gli utenti registrati al servizio di callback
    final private ConcurrentHashMap<String, IntRMIClient> userToNotify;

    public WinSomeServer(String pathConfigurationFile) throws IOException {
        //inizializzo il file di configurazione tramite ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        serverConfiguration = mapper.readValue(Paths.get(pathConfigurationFile).toFile(), ServerConfiguration.class);

        readyToWrite = new ArrayList<>();
        registeredUsers = new ConcurrentHashMap<>();
        loggedUsers = new ConcurrentHashMap<>();
        userToNotify = new ConcurrentHashMap<>();
        workersThreadPool = Executors.newFixedThreadPool(serverConfiguration.getnWorker());
        fixedTaskScheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void run() {
        //***** INIZIALIZZAZIONE A PARTIRE DALL'ULTIMO BACKUP *****//
        try{
            //apro il file di backup indicato nelle configuarzioni del server
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode rootNode = mapper.readValue(Paths.get(serverConfiguration.getBackupJsonFile()).toFile(), ObjectNode.class);
            ArrayNode childNode = (ArrayNode) rootNode.get("users");
            for(JsonNode userObject : childNode){
                //prima aggiungo tutti gli utenti e le relative informazioni alla lista degli utenti iscritti

                String username = userObject.get("username").asText();
                String password = userObject.get("password").asText();
                ArrayList<String> tagList = new ArrayList<>();
                for(JsonNode tag : userObject.get("tags"))
                    tagList.add(tag.asText());
                //creo un nuovo utente con le informazioni ottenute
                User newUser = new User(username,password,tagList);
                Wallet newUserWallet = newUser.getWallet();
                //aggiungo al wallet associato all'utente tutte le transazioni relative a tale utente
                for(JsonNode transaction : userObject.get("wallet").get("transactions")){
                    newUserWallet.addTransaction(transaction.asText());
                }
                //registro il nuovo utente aggiungendolo alla struttura dati
                registeredUsers.put(username, newUser);
            }
            for(JsonNode userObject : childNode){
                //poi creo tutte le relazioni fra utenti: aggiungo follower e post con reazioni
                String username = userObject.get("username").asText();
                User userBackup = registeredUsers.get(username);
                ArrayNode followers = (ArrayNode) userObject.get("followers");
                for(JsonNode follower : followers){
                    User userFollower = registeredUsers.get(follower.get("username").asText());
                    userBackup.getFollowers().add(userFollower);
                }
                ArrayNode following = (ArrayNode) userObject.get("following");
                for(JsonNode followed : following){
                    User userFollowed = registeredUsers.get(followed.get("username").asText());
                    userBackup.getFollowedUsers().add(userFollowed);
                }
                //ricreo il blog dell'utente tramite i post presenti nel file di configurazione
                ArrayNode blogArrayNode = (ArrayNode) userObject.get("blog");
                for(JsonNode postObject : blogArrayNode){
                    String postAuthor = postObject.get("author-username").asText();
                    String title = postObject.get("title").asText();
                    long idPost = postObject.get("idPost").asLong();
                    long iteration = postObject.get("iteration").asLong();
                    String postContent = postObject.get("content").asText();
                    User postAuthorUser = registeredUsers.get(postAuthor);
                    Post newPost = new Post(postAuthorUser, title, postContent, idPost, iteration);
                    //ottengo e aggiungo tutte le reaction al post
                    ArrayNode reactionArrayNode = (ArrayNode) postObject.get("reactions");
                    for(JsonNode reaction : reactionArrayNode){
                        int type = reaction.get("type").asInt();
                        //attributo fondamentale per capire se la reaction è stata già calcolata dal sistema delle ricompense
                        boolean isNew = reaction.get("isNew").asBoolean();
                        Reaction newReaction;
                        String reactionAuthor = reaction.get("author").asText();
                        if(type==0){
                            //rate
                            int rate = reaction.get("rate").asInt();
                            User reactionUser = registeredUsers.get(reactionAuthor);
                            newReaction = Reaction.newVote(reactionUser, rate, isNew);
                        }
                        else{
                            //comment
                            String commentContent = reaction.get("content").asText();
                            User reactionUser = registeredUsers.get(reactionAuthor);
                            newReaction = Reaction.newComment(reactionUser, commentContent, isNew);
                        }
                        //aggiungo la reaction al post
                        newPost.getReaction().add(newReaction);
                    }
                    //aggiungo il post al blog dell'utente
                    userBackup.getBlog().put(idPost, newPost);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Nessun file di backup trovato: il winsome.server viene inizializzato da 0");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //***** CREAZIONE E ESPORTAZIONE OGGETTO REMOTO RMI PER REGISTRAZIONE A WINSOME E MECCANISMO CALLBACK *****//
        ImplRMIServer RMIobject;
        Registry registry;
        try {
            //creo l'oggetto remoto
            RMIobject = new ImplRMIServer(this);
            //lo esporto e ottongo lo stub
            IntRMIServer stub = (IntRMIServer) UnicastRemoteObject.exportObject(RMIobject,0);
            //creo il registro in ascolto sulla porta settata nel file di configurazione
            LocateRegistry.createRegistry(serverConfiguration.getRegistryPort());
            //ottengo il regiatro appena creato
            registry = LocateRegistry.getRegistry(serverConfiguration.getRegistryPort());
            //registro il mio oggetto esportato
            registry.bind(serverConfiguration.getRMIObjectbindingName(), stub);
        } catch (RemoteException | AlreadyBoundException e) {
            System.out.println("FATAL ERROR");
            e.printStackTrace();
            return;
        }

        try(ServerSocketChannel listenChannel = ServerSocketChannel.open()) {
            //***** CREAZIONE E SOTTOMISSIONE DEL TASK PERODICO PER IL CALCOLO DELLE RICOMPENSE *****//
            InetSocketAddress multicastGroup = new InetSocketAddress(InetAddress.getByName(serverConfiguration.getMulticastAddress()), serverConfiguration.getMulticastPort());
            fixedTaskScheduler.scheduleAtFixedRate(new RewardCalculator(this, multicastGroup), 1, serverConfiguration.getTimerRewardMin(), TimeUnit.MINUTES);

            //***** CREAZIONE E SOTTOMISSIONE DEL TASK PERODICO PER IL BACKUP SU DISCO *****//
            fixedTaskScheduler.scheduleAtFixedRate(new DataBackup(this), 1, serverConfiguration.getTimerBackupMin(), TimeUnit.MINUTES);

            //*****  BINDING LISTEN SOCKET *****//
            listenChannel.socket().bind(new InetSocketAddress(serverConfiguration.getIP(),serverConfiguration.getTCPport()));
            listenChannel.configureBlocking(false);
            Selector selector = Selector.open();

            //registro il channel associato alla listenSocket nel selettore
            listenChannel.register(selector, SelectionKey.OP_ACCEPT);
            while(!Thread.interrupted()){
                int r = selector.select();
                synchronized (readyToWrite){
                    //scorro la lista delle SelectionKey pronte in scrittura e ne registro l'interesse in scrittura
                    Iterator<SelectionKey> it = readyToWrite.iterator();
                    while(it.hasNext()){
                        SelectionKey key = it.next();
                        it.remove();
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                }
                if (r==0)
                    // mi sono svegliato solo per registrare un'operazione di scrittura
                    continue;

                //ottengo lista selectionKey relative ad un canale pronto per qualche operazione di interesse
                Set<SelectionKey> readyKeys = selector.selectedKeys();

                //itero fra le chiavi pronte
                Iterator<SelectionKey> it = readyKeys.iterator();
                while(it.hasNext()){
                    SelectionKey key = it.next();
                    //sto gestendo il canale pronto: lo rimuovo dai canali pronti
                    it.remove();
                    if(key.isAcceptable()){
                        registerNewClient(selector, key);
                    }
                    else if(key.isReadable()){
                        readRequest(selector, key);
                    }
                    else if(key.isWritable()){
                        sendResponse(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //***** TERMINAZIONE WINSOME SERVER *****//
            System.out.println("Termino tutti i thread attivi");
            try {
                //termino i threadPool
                workersThreadPool.shutdown();
                workersThreadPool.awaitTermination(60, TimeUnit.SECONDS);
                fixedTaskScheduler.shutdown();
                fixedTaskScheduler.awaitTermination(60, TimeUnit.SECONDS);

                //elimino oggetto esportato dal registry
                registry.unbind(serverConfiguration.getRMIObjectbindingName());
                //eseguo unexport dell'oggetto esportato
                UnicastRemoteObject.unexportObject(RMIobject, true);

                //eseguo backup finale
                System.out.println("Eseguo backup finale");
                new DataBackup(this).run();
            } catch (RemoteException | NotBoundException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //effects: registra un nuovo client da gestire nel selector
    public void registerNewClient(Selector selector, SelectionKey key) throws IOException {
        //il canale pronto è quello di listen
        ServerSocketChannel listenChannel = (ServerSocketChannel) key.channel();
        SocketChannel client = listenChannel.accept();
        System.out.println("***** Nuova connessione con host: " + client.getRemoteAddress() + " *****");
        //modalità non bloccante
        client.configureBlocking(false);
        //aspetto prima richiesta dal winsome.client: registro come operazione di interesse la lettura
        SelectionKey keyClient = client.register(selector,SelectionKey.OP_READ);
        //alloco il bytebuffer che userò per ottenere la richiesta
        ByteBuffer buf = ByteBuffer.allocate(MAXDIMBUFFER);
        keyClient.attach(buf);
    }

    //effects: legge qualcosa (non per forza una richiesta completa, approccio non bloccante) inviato dal client associato alla SelectionKey key
    public void readRequest(Selector selector, SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        if(buf.hasRemaining()) {
            System.out.println("Channel associato alla connessione con host " + client.getRemoteAddress() + " pronto in lettura: leggo ciò che il client mi ha mandato");

            //una read attraverso un channel equivale ad una scrittura nel buffer
            int n = client.read(buf);
            if(n == -1){
                //winsome.client ha chiuso la connessione
                System.out.println("***** Chiudo connessione con host " + client.getRemoteAddress() + " *****");
                key.cancel();
                client.close();
                return;
            }
            //buf è in modalità scrittura: buf.position indica numero di caratteri letti
            String fromClient = new String(buf.array(),0,buf.position(), StandardCharsets.UTF_8);


            //dopo una read non sono sicuro di aver letto una richiesta completa: esamino ciò che ho letto
            if(fromClient.contains("\r\n\r\n")){ //ho letto tutta l'intestazione?
                if(fromClient.contains("GET") || fromClient.contains("DELETE")){
                    //non contengono un body, passo direttamente la richiesta ad un worker
                    key.interestOps(0);
                    workersThreadPool.execute(new RequestHandler(selector,key,this, fromClient));
                }
                else{
                    int index = fromClient.indexOf("Content-Length: ");
                    if(index == -1){
                        //il metodo è PUT o POST e manca il campo Content-Length
                        key.interestOps(0);
                        //passo al thread una stinga == null così da sollevare un'eccezione e mandare un messaggio d'errore al client
                        workersThreadPool.execute(new RequestHandler(selector,key,this,null));
                        return;
                    }
                    //header del contente-length
                    String contentLenghtHeader = fromClient.substring(index);

                    //prelevo header
                    String header = fromClient.split("\r\n\r\n")[0];
                    //ottengo dimensione body
                    int bodySize = Integer.parseInt(contentLenghtHeader.split(" ")[1].split("\r\n")[0]);

                    //aspetto di leggere tutto il corpo della richiesta e, una volta fatto, passo la richiesta al worker
                    if(fromClient.length() == bodySize + header.length() + 4){
                        key.interestOps(0);
                        workersThreadPool.execute(new RequestHandler(selector,key,this,fromClient));
                    }
                }
            }
        }
        else{
            System.out.println("Buffer troppo pieno!");
            //il winsome.client sta inviando una richiesta troppo lunga
            key.interestOps(0);
            //passo al thread una stinga == null così da sollevare un'eccezione e mandare un messaggio d'errore al client
            workersThreadPool.execute(new RequestHandler(selector,key,this,null));
        }
    }

    //effects: invia al client il contenuto rimanente (non ancora inviato, approccio non bloccante) del ByteBuffer associato alla SelectionKey key
    public void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        //prelevo il buffer associato alla connessione
        ByteBuffer buf = (ByteBuffer) key.attachment();
        if(buf.hasRemaining()){
            //ho ancora altro da "leggere" dal buffer e da "scrivere" nel channel
            System.out.println("Channel associato alla connessione con host " + client.getRemoteAddress() + " pronto in scrittura: scrivo all'host");
            client.write(buf);
        }else{
            System.out.println("Channel associato alla connessione con host " + client.getRemoteAddress() + ": non ho più nulla da inviare, attendo che mi invii qualcosa da leggere");
            key.interestOps(SelectionKey.OP_READ);
            //alloco un nuovo buffer per la lettura della successiva richiesta
            ByteBuffer bufForNewReq = ByteBuffer.allocate(MAXDIMBUFFER);
            key.attach(bufForNewReq);
        }
    }


    //***** GETTERS *****//
    public ConcurrentHashMap<String, IntRMIClient> getUserToNotify() {
        return userToNotify;
    }

    public ArrayList<SelectionKey> getReadyToWrite() {
        return readyToWrite;
    }

    public ConcurrentHashMap<String, User> getRegisteredUser() {
        return registeredUsers;
    }

    public ConcurrentHashMap<Token, User> getLoggedUser() {
        return loggedUsers;
    }

    public ServerConfiguration getServerConfiguration() {
        return serverConfiguration;
    }
}
