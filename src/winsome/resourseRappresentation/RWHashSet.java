package winsome.resourseRappresentation;

import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RWHashSet <T>{
    //HashSet su cui gestisc accessi concorrenti
    final private HashSet<T> map;
    //lock per l'accesso in mutua esclusione sulla struttura dati
    final private Lock readLock;
    final private Lock writeLock;

    public RWHashSet (){
        map = new HashSet<>();
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
    }

    //effects: aggiunge un elemento al set. Ritorna true se l'elemento è stato aggiunto; false altrimenti
    public boolean add (T elem){
        try{
            writeLock.lock();
            return map.add(elem);
        }finally {
            writeLock.unlock();
        }
    }
    //effects: elimina un elemento dal set. Ritorna true se l'elemento è stato rimosso; false altrimenti
    public boolean remove (T elem){
        try{
            writeLock.lock();
            return map.remove(elem);
        }finally {
            writeLock.unlock();
        }
    }

    //effects: verifica la presenza di un elemento nel set. Ritorna ture se l'elemento è presente; false altrimenti
    public boolean contains (T elem){
        try{
            readLock.lock();
            return map.contains(elem);
        }finally {
            readLock.unlock();
        }
    }
    //effects: ritorna una copia dell'HashMap che mantiene. In tal modo ciascuno potrà iterare sulla lista senza problemi di concorrenza
    public HashSet<T> getHashSet() {
        try{
            this.readLock.lock();
            return new HashSet<>(this.map);
        }finally {
            this.readLock.unlock();
        }
    }
}
