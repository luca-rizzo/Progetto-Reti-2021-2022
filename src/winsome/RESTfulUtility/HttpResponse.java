package winsome.RESTfulUtility;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Scanner;

public class HttpResponse{

    //enumerazione relativa a tutti i codici di risposta inviati dal server
    public enum StatusCode{
        success200("200", "OK"),
        success201("201", "Created"),
        redirection304("304","Not Modified"),
        error400("400", "Bad Request"),
        error401("401", "Unauthorized"),
        error404("404", "Not Found"),
        error405("405","Method Not Allowed"),
        error406("406", "Not Acceptable"),
        error409("409", "Conflict"),
        error412("412","Precondition Failed"),
        error413("413", "Request entity Too Large");
        private final String code;
        private final String phrase;
        StatusCode(String code, String phrase) {
            this.code = code;
            this.phrase = phrase;
        }
        public String getCode() {
            return code;
        }
        public String getPhrase() {
            return phrase;
        }
    }

    //contiene tutti gli header della risposta
    private final HashMap<String,String> headers;
    //gestisco solo contenuti JSON: il corpo sarà una stringa in JSON
    private String body;
    //versione HTTP
    private String version = "HTTP/1.1";
    //status code della risposta
    private String statusCode;
    //frase relativa allo status code
    private String phrase;


    private HttpResponse(){
        headers = new HashMap<>();
        body = null;
    }

    //effects: ritorna un oggetto HttpResponse con la riga di stato settata in base allo status code passato come argomento
    static public HttpResponse newHttpResponse(StatusCode code){
        HttpResponse response = new HttpResponse();
        response.statusCode = code.getCode();
        response.phrase = code.getPhrase();
        response.headers.put("Server", "WinSome Server");
        return response;
    }

    //effects: parsa la stringa plainText passata come argomento e genera l'oggetto HttpResponse associato.
    //solleva una ParseException in caso di errori nel parsing (se la stringa non rispetta sintassi Http)
    static public HttpResponse newHttpResponseFromString (String plainText) throws ParseException {
        HttpResponse response = new HttpResponse();
        Scanner scanner = new Scanner(plainText);
        String line;
        line = scanner.nextLine();
        String[] tokens = line.replaceAll("\r\n","").split(" ");
        //status line ha 3 token
        if(tokens.length!=3)
            throw new ParseException(plainText, 0);
        response.version = tokens[0];
        response.statusCode = tokens[1];
        response.phrase = tokens[2];
        while (!(line = scanner.nextLine()).isEmpty()){ //devo giungere alla linea di separazione dal "possibile body"
            tokens = line.replaceAll("\r\n","").split(": ");
            //ogni header ha 2 token
            if(tokens.length!=2)
                throw new ParseException(line, 0);
            response.headers.put(tokens[0], tokens[1]);
        }
        if( response.headers.containsKey("Content-Length")){
            //c'è un body
            line = scanner.nextLine();
            String bodyText = plainText.substring(plainText.indexOf(line));
            int length = bodyText.length();
            //controllo che il contenuto del body sia di dimensione uguale a quella dichiarata nel campo Content-length
            if(length != Integer.parseInt (response.headers.get("Content-Length")))
                throw new ParseException(bodyText,0);
            response.body = bodyText;
        }
        return response;
    }

    //effects: parsa i dati ottenuti tramite InputStream in e genera l'oggetto HttpResponse associato.
    //solleva una ParseException in caso di errori nel parsing (se i dati inviati non rispettano sintassi Http)
    static public HttpResponse newHttpResponseFromStream (BufferedReader reader) throws ParseException, IOException {
        HttpResponse response = new HttpResponse();
        String line;
        line = reader.readLine();
        String[] tokens = line.replaceAll("\r\n","").split(" ");
        //status line ha 3 token
        if(tokens.length<3)
            throw new ParseException(line, 0);
        response.version = tokens[0];
        response.statusCode = tokens[1];
        response.phrase = tokens[2];
        while (!(line = reader.readLine()).isEmpty()){ //devo giungere al carattere di separazione dal "possibile body"
            tokens = line.replaceAll("\r\n","").split(": ");
            //System.out.println(tokens[0] + " " + tokens[1]);
            if(tokens.length!=2)
                throw new ParseException(line, 0);
            response.headers.put(tokens[0], tokens[1]);

            //ogni header ha 2 token
        }
        if( response.headers.containsKey("Content-Length")){
            //c'è un body
            int bodyLenght = Integer.parseInt(response.headers.get("Content-Length"));
            char[] body = new char[bodyLenght];
            int characterRead = reader.read(body,0,body.length);
            if(characterRead != bodyLenght){
                throw new ParseException(new String(body),0);
            }
            response.body = new String(body);
        }
        return response;
    }

    //effects: aggiunge header alla risposta Http
    public void addHeader(String headerName, String value){
        headers.put(headerName,value);
    }

    //effects: aggiunge un body alla risposta Http. Verrà settato automaticamente il campo Content-Length e Content-Type (trattiamo solo corpi testuali formato JSON)
    public void setBody(String body) {
        this.body = body;
        this.addHeader("Content-Length", "" + body.length());
        this.addHeader("Content-Type", "application/json");
    }

    //effects: ritorna il ByteBuffer associato all'oggetto che rappresenta una risposta Http
    public ByteBuffer getRelateByteBuffer() throws UnsupportedEncodingException {
        StringBuilder content = new StringBuilder();
        content.append(version + " " + statusCode + " " + phrase + "\r\n");
        for (HashMap.Entry<String, String> entry : headers.entrySet()) {
            content.append(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        content.append("\r\n");
        if(body!=null){
            content.append(body);
        }
        return ByteBuffer.wrap(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    //effects: ritorna la stringa associata all'oggetto che rappresenta una risposta Http
    public String getRelateString() throws UnsupportedEncodingException {
        return new String(getRelateByteBuffer().array());
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
