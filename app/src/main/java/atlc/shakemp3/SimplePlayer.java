package atlc.shakemp3;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class SimplePlayer
{
    OnPlayerEventListener mListener;
    Activity mActivity;

    //give access to the gui
    public ArrayList<String> songs = null;
    public ArrayList<String> songsPaths = null;
    public String currentSongPath = null;
    public boolean isPaused = false;
    public int currentPosition = 0;
    public int currentDuration = 0;

    //single instance
    public static MediaPlayer player;

    //getting gui player interface
    public SimplePlayer(Activity ma)
    {
        mActivity = ma;
        mListener = (OnPlayerEventListener) mActivity;
    }

    //initialize the player
    public void init(ArrayList<String>_songs, ArrayList<String> _paths, int currentSong)
    {
        songs = _songs;
        songsPaths = _paths;
        currentPosition = currentSong;

        if(player == null)
        {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {

                    player.stop();
                    player.reset();

                    nextSong();
                    mListener.onPlayerSongComplete();

                }


            });
        }
    }

    //stop the current song
    public void stop()
    {
        if(player != null)
        {
            if(player.isPlaying())
                //stop music
                player.stop();

            //reset pointer
            player.reset();
        }
    }

    //setting currentSongPath
    public void setCurrentSongPath(String path)
    {
        this.currentSongPath = path;
    }

    //pause the current song
    public void pause()
    {
        if(!isPaused && player != null)
        {
            player.pause();
            isPaused = true;
        }
    }

    //playing the current song
    public void play()
    {
        if(player != null)
        {
            if(!isPaused && !player.isPlaying())
            {
                if(songs != null)
                {
                    if(songs.size() > 0)
                    {
                        try {
                            //getting file path from data
                            Uri u = Uri.fromFile(new File(songsPaths.get(currentPosition)));

                            //set player file
                            player.setDataSource(mActivity,u);
                            //loading the file
                            player.prepare();
                            //getting song total time in milliseconds
                            currentDuration = player.getDuration();

                            //start playing music!
                            player.start();
                            mListener.onPlayerSongStart(songs.get(currentPosition)
                                    ,currentDuration,currentPosition);
                        }
                        catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }

            }else
            {
                player.start();
                isPaused = false;
            }
        }
    }


    public boolean isPlaying()
    {
        return player.isPlaying();
    }

    //playing the next song in the array
    public  void nextSong()
    {
        if(player != null)
        {
            if(isPaused)
                isPaused = false;

            if(player.isPlaying())
                player.stop();

            player.reset();

            if((currentPosition + 1) == songs.size())
                currentPosition = 0;
            else
                currentPosition  = currentPosition + 1;

            play();
        }
    }

    //playing the previous song in the array
    public void previousSong()
    {
        if(player != null)
        {
            if(isPaused)
                isPaused = false;

            if(player.isPlaying())
                player.stop();

            player.reset();

            if(currentPosition - 1 < 0)
                currentPosition = songs.size();
            else
                currentPosition = currentPosition -1;

            play();
        }
    }

    public void resetPlayer(int a)
    {
        isPaused = false;

        if(player != null && player.isPlaying())
        {
            player.stop();
            player.reset();
        }else if(player != null && !player.isPlaying())
        {
            currentPosition = a;
            player.stop();
            player.reset();
            isPaused = false;
            play();
        }
    }

    //getting new position for playing by the gui seek bar
    public void setSeekPosition(int msec)
    {
        if(player != null)
            player.seekTo(msec);
    }

    //getting the current duration of music
    public int getSeekPosition()
    {
        if(player != null)
            return  player.getDuration();
        else
            return -1;
    }

    public int getPosition()
    {
        if(player!=null)
            return player.getCurrentPosition();
        else return 0;
    }

    public int getCurrentSong()
    {
        return currentPosition;
    }

    public String getCurrentSongTitle()
    {
        return songs.get(currentPosition);
    }

}