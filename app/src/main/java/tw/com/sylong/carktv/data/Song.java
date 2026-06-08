package tw.com.sylong.carktv.data;

public class Song {
    public long id;
    public String catalogKey = "";
    public String number = "";
    public String title = "";
    public String artist = "";
    public String language = "";
    public String category = "";
    public String mediaUri = "";
    public String lyricUri = "";
    public String source = "";
    public String license = "";
    public String updatedAt = "";
    public boolean favorite;

    public String displayName() {
        String main = isBlank(artist) ? title : artist + " - " + title;
        if (!isBlank(number)) {
            main = number + "  " + main;
        }
        if (!isBlank(source)) {
            main = main + "  [" + source + "]";
        }
        return main;
    }

    public boolean hasPlayableMedia() {
        return !isBlank(mediaUri);
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

