package winsome.server;

import winsome.resourseRappresentation.Post;
import winsome.resourseRappresentation.Reaction;
import winsome.resourseRappresentation.User;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class RewardCalculator implements Runnable{
    private final byte[] MSG_TO_SEND = "Rewards have been calculated".getBytes(StandardCharsets.UTF_8);

    //riferimenti prelevati da winsomeServer
    private final ConcurrentHashMap<String, User> registeredUser;
    private final InetSocketAddress multicastGroup;
    private final int authorPercentage;

    public RewardCalculator(WinSomeServer winSomeServer, InetSocketAddress multicastGroup) {
        //prelevo i riferimenti da winsomeServer
        this.registeredUser = winSomeServer.getRegisteredUser();
        this.authorPercentage = winSomeServer.getServerConfiguration().getAuthorPercentage();
        //salvo indirizzo multicast a cui inviare stringhe di notifica calcolo ricompense
        this.multicastGroup = multicastGroup;
    }

    @Override
    public void run() {
        //scorro tutti i post di tutti i blog e calcolo tutte le ricompense esaminando le reaction a ciascun post

        for(User user : registeredUser.values()){
            //scorro tutti gli utenti registrati
            for(Post post : user.getBlog().values()){
                //scorro lungo tutti i post del blog
                //mantengo riferimenti a curatori del post
                HashSet<User> curatoriPost = new HashSet<>();

                //sommatoria primo termine
                double sumLp = 0;

                //server per calcolare valore C_p di ciascuna nuova persona che ha commentato il post

                HashMap<User, Integer> numberOfNewComment = new HashMap<>();
                //thread safe perchè getHashSet() ritorna una copia delle reaction su cui possiamo iterare senza concorrenza
                for(Reaction reaction : post.getReaction().getHashSet()) {
                    if (reaction.isNew()) {
                        //non ho ancora calcolato nel meccanismo di ricompense tale reaction

                        //non dovrò più calcolare tale reaction alla prossima iterazione
                        reaction.setNew(false);
                        if (reaction.getType() == 0) {
                            //è un voto: contribuisce al primo termine della formula
                            if (reaction.getRate() == 1) {
                                //è un curatore solo se il voto è positivo
                                curatoriPost.add(reaction.getAuthor());
                            }
                            sumLp += reaction.getRate();
                        }
                        if (reaction.getType() == 1) {
                            curatoriPost.add(reaction.getAuthor());
                            Integer currentNumberOfComment = numberOfNewComment.get(reaction.getAuthor());
                            if (currentNumberOfComment == null) {
                                numberOfNewComment.put(reaction.getAuthor(), 1);
                            } else {
                                numberOfNewComment.put(reaction.getAuthor(), currentNumberOfComment + 1);
                            }
                        }
                    }
                }

                //calcolo ricompensa
                double termineUno = Math.log(Math.max(sumLp, 0) + 1);
                double termineDue = 1;
                for(Map.Entry<User, Integer> entry : numberOfNewComment.entrySet()) {
                    //eseguo sommatoria di tutti i valori C_p di tutte le persone che hanno commentato
                    double fattoreCommentiUtente = 2.0 / (1 + Math.exp(-(entry.getValue()-1)));
                    termineDue += fattoreCommentiUtente;
                }
                termineDue = Math.log(termineDue);
                double guadagnoTotale = (termineUno + termineDue) / post.incrementAndGetIterazioni();
                if (guadagnoTotale > 0) {
                    //approssimo alla quinta cifra decimale
                    guadagnoTotale = (double)Math.round(guadagnoTotale * 100000d) / 100000d;
                    double guadagnoAutore = (double)Math.round(guadagnoTotale * authorPercentage/100 * 100000d) / 100000d ;
                    double guadagnoCuratore = (double)Math.round(guadagnoTotale * (100 - authorPercentage) / 100 / curatoriPost.size() * 100000d) / 100000d;
                    if(guadagnoAutore > 0)
                        post.getAuthor().getWallet().addTransaction(guadagnoAutore + " " + new Timestamp(System.currentTimeMillis()) + " Author of post: " + post.getId());
                    if(guadagnoCuratore > 0)
                        for(User curatore : curatoriPost){
                            curatore.getWallet().addTransaction(guadagnoCuratore + " " + new Timestamp(System.currentTimeMillis()) + " Curator of post: " + post.getId());
                        }
                }
            }
        }
        System.out.println("Reward calculated!");
        try(DatagramSocket socket = new DatagramSocket()){
            //invio notifiche a tutti i client
            DatagramPacket notification = new DatagramPacket(MSG_TO_SEND, MSG_TO_SEND.length, multicastGroup);
            socket.send(notification);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
