package atlc.shakemp3;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;

public class SongList extends AppCompatActivity {

    Cursor cursor;
    ArrayList<String>songs = new ArrayList<>();
    ArrayList<String>paths = new ArrayList<>();
    long[] albums;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.song_list);

        ArrayList<Category> categories = new ArrayList<Category>();
        Drawable res = getResources().getDrawable(R.drawable.ic_action_name);

        //Seleccionamos la música que hay en el almacenamiento del teléfono.
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        //Establecemos los atributos de cada una de las canciones.
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
        };

        cursor = this.managedQuery(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);


        //Nos movemos de canción en canción para obtener los atributos previamente definidos.
        ArrayList<Long> aux = new ArrayList<>();
        while(cursor.moveToNext()){
            songs.add(cursor.getString(2));
            paths.add(cursor.getString(3));
            aux.add(cursor.getLong(6));
        }


        //Obtenemos la ID de las carátulas de los álbumes.
        albums = new long[aux.size()];

        for(int i = 0 ; i < aux.size() ; i++)
        {
            albums[i] = aux.get(i);
        }


        for(int i = 0 ; i < songs.size() ; i++)
        {
            categories.add(new Category(songs.get(i), songs.get(i), paths.get(i), res));
        }

        //Añadimos las canciones al ListView.

        ListView lv = (ListView) findViewById(R.id.listView);
        final AdapterCategory adapter = new AdapterCategory(this, categories);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int pos = position;
                Category c = (Category) adapter.getItem(pos);

                Intent intent = new Intent(getBaseContext(), MainActivity.class);
                intent.putExtra("SONG_LIST", songs);
                intent.putExtra("SONG_PATH_LIST", paths);
                intent.putExtra("SONG_PATH", c.getPath());
                intent.putExtra("SONG_INDEX", pos);
                intent.putExtra("SONG_NAME", c.getTitle());
                intent.putExtra("ALBUM_ID", albums);
                startActivity(intent);
                finish();

            }
        });
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(getBaseContext(), MainActivity.class);
        startActivity(intent);
        super.onBackPressed();
    }
}
