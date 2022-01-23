package winsome.client;


import winsome.RESTfulUtility.HttpResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;

public interface APIClientInterface {
    HttpResponse register(String username, String password, ArrayList<String> tags) throws RemoteException, NotBoundException, ParseException, UnsupportedEncodingException;
    HttpResponse login(String username, String password) throws IOException, ParseException;
    HttpResponse accountPrivateInfo() throws IOException, ParseException;
    HttpResponse viewBlog() throws IOException, ParseException;
    HttpResponse createPost (String title, String content) throws IOException, ParseException;
    HttpResponse rewinPost (String idPost) throws IOException, ParseException;
    HttpResponse deletePost ( String idPost ) throws IOException, ParseException;
    HttpResponse logout() throws IOException, ParseException, InterruptedException;
    HttpResponse followUser(String username) throws IOException, ParseException;
    HttpResponse unfollowUser(String username) throws IOException, ParseException;
    HttpResponse viewFeed() throws IOException, ParseException;
    HttpResponse ratePost(String idPost, String rate) throws IOException, ParseException;
    HttpResponse addComment(String idPost, String content) throws IOException, ParseException;
    HttpResponse showPost(String idPost) throws IOException, ParseException ;
    HttpResponse listUsers() throws IOException, ParseException;
    HttpResponse listFollowing() throws IOException, ParseException;
    HashSet<String> listFollowers();
}
