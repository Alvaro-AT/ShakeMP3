package atlc.shakemp3;

public interface OnPlayerEventListener {
    void onPlayerSongComplete();
    void onPlayerSongStart(String Title,int songDuration,int songPosition);
}
