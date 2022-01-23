package winsome.resourseRappresentation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Wallet {
    //lock per l'acccesso mutuamente esclusivo allo stato dell'oggetto
    final private Lock readLock;
    final private Lock writeLock;
    //lista di transazioni
    final private ArrayList<String> transactions;
    //ammontare del portafoglio in WinCoin
    private double amount;

    public Wallet (){
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
        transactions = new ArrayList<>();
        amount = 0;
    }

    //effects: aggiunge la transazione alla lista di transazioni del portafoglio e aggiorna l'ammontare del portafoglio
     public void addTransaction(String transaction){
        String[] tokens = transaction.split(" ");
        //formato transazione "incremento timestamp Author/Curator of post idPost"
        if(tokens.length < 3){
            throw new IllegalArgumentException();
        }
        double increment = Double.parseDouble(tokens[0]);
        try{
            writeLock.lock();
            //acquisisco la lock in scrittura in quanto modifico lo stato dell'oggetto
            transactions.add(transaction);
            amount+=increment;
        }finally{
            //rilascio la lock
            writeLock.unlock();
        }
     }

    //effects: ottieni una copia della lista delle transazioni relative al wallet
    public ArrayList<String> getTransactions() {
        try{
            readLock.lock();
            //acquisisco la lock in lettura per leggere stato oggetto
            return new ArrayList<>(transactions);
        }finally{
            //rilascio la lock
            readLock.unlock();
        }
    }

    //effects: ottieni ammontare del portafoglio in wincoin
    public double getAmount() {
        try{
            readLock.lock();
            return amount;
        }finally{
            readLock.unlock();
        }
    }
    //effects: ottieni ammo ntare del portafoglio in BTC. Ritorna -1 in caso di errori con il tasso di cambio
    public double getAmountInBitcoin() {
        HttpURLConnection http = null;
        try {
            readLock.lock();
            //faccio una richiesta al sito random.org per ottenere tasso di cambio: esso mi ritornerà un intero tra 1 e 99 che userò per il mio tasso di cambio
            URL url = new URL("https://www.random.org/integers/?num=1&min=1&max=99&col=1&base=10&format=plain&rnd=new");
            http = (HttpURLConnection) url.openConnection();
            if (http.getResponseCode() == 200) {
                //ho ottenuto la risposta
                try(BufferedReader responseReader = new BufferedReader(new InputStreamReader(http.getInputStream()))){
                    //utiizzo il valore ottenuto per ricreare tasso di cambio
                    String tassoDiCambio = "0.000" + responseReader.readLine();
                    Double cambioInDouble = Double.parseDouble(tassoDiCambio);
                    System.out.println(cambioInDouble);
                    return cambioInDouble * amount;
                }
            } else {
                //problemi con il sistema di cambio
                return -1;
            }

        } catch (IOException e) {
            //problemi con il sistema di cambio
            return -1;
        } finally {
            if(http != null)
                http.disconnect();
            readLock.unlock();
        }
    }

    //effects: ritorna una copia "congelata" del portafoglio in cui sono sincronizzati tutti i valori contenuti
    public Wallet copyWallet(){
        Wallet copyWallet = new Wallet();
        try{
            readLock.lock();
            for(String transaction: transactions){
                copyWallet.addTransaction(transaction);
            }
            return copyWallet;
        }finally{
            readLock.unlock();
        }
    }
}
