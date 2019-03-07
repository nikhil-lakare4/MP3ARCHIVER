package com.mp3archiver;
import com.mpatric.mp3agic.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static class Song {

        private final String artist;
        private final String year;
        private final String album;
        private final String title;

        public Song(String artist, String year, String album, String title) {
            this.artist = artist;
            this.year = year;
            this.album = album;
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }

        public String getYear() {
            return year;
        }

        public String getAlbum() {
            return album;
        }

        public String getTitle() {
            return title;
        }
    }

    public static class SongServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder builder = new StringBuilder();
            try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase")) {

                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select * from SONGS");

                while (rs.next()) {
                    builder.append("<tr class=\"table\">")
                            .append("<td>").append(rs.getString("year")).append("</td>")
                            .append("<td>").append(rs.getString("artist")).append("</td>")
                            .append("<td>").append(rs.getString("album")).append("</td>")
                            .append("<td>").append(rs.getString("title")).append("</td>")
                            .append("</tr>");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            String string = "<html><h1>Your Songs</h1><table><tr><th>Year</th><th>Artist</th><th>Album</th><th>Title</th></tr>" + builder.toString() + "</table></html>";
            resp.getWriter().write(string);
        }
    }

    public static void main(String[] args) throws Exception {
        // write your code here
        if(args.length != 1){
            throw new IllegalArgumentException("Specified Directory is not Valid");
        }
        String directory = args[0];
        Path mp3Directory = Paths.get(directory);

        if(!Files.exists(mp3Directory)){
            throw new IllegalArgumentException("File does not exist!");
        }

        List<Path> mp3Paths = new ArrayList<>();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(mp3Directory, "*.mp3")) {
            paths.forEach(p -> {
                System.out.println("Found : " + p.getFileName().toString());
                mp3Paths.add(p);
            });
        }

        List<Song> songs = mp3Paths.stream().map(path -> {
            try {
                Mp3File mp3file = new Mp3File(path);
                ID3v2 id3 = mp3file.getId3v2Tag();
                return new Song(id3.getArtist(), id3.getYear(), id3.getAlbum(), id3.getTitle());
            } catch (IOException | UnsupportedTagException | InvalidDataException e) {
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toList());

        System.out.println("Yo Bitch");

        try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase;AUTO_SERVER=TRUE")) {

            String createTable = "create table IF NOT EXISTS SONGS (id bigint auto_increment,artist varchar(255),year varchar(255),album varchar(255),title varchar(255)); delete from SONGS;";

            try(Statement st1 = conn.createStatement()){
                st1.executeUpdate(createTable);
            }catch(SQLException e){
                e.printStackTrace();
            }

            PreparedStatement st = conn.prepareStatement("insert into SONGS (artist, year, album, title) values (?, ?, ?, ?);");

            for (Song song : songs) {
                st.setString(1, song.getArtist());
                st.setString(2, song.getYear());
                st.setString(3, song.getAlbum());
                st.setString(4, song.getTitle());
                st.addBatch();
            }

            int[] updates = st.executeBatch();
            System.out.println("Inserted [" + updates.length + "] records into the database");
        }

        Server server = new Server(8081);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(System.getProperty("java.io.tmpdir"));
        server.setHandler(context);

        context.addServlet(SongServlet.class, "/songs");
        server.start();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8081/songs"));
        }
    }
}

