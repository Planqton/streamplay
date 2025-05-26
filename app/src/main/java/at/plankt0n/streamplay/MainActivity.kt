package at.plankt0n.streamplay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import at.plankt0n.streamplay.ui.PlayerFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Direkt PlayerFragment laden
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PlayerFragment())
                .commit()
        }
    }
}
