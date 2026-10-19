package de.bedrockchat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import de.bedrockchat.databinding.ActivityMainBinding
import de.bedrockchat.security.TokenStore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge: Inhalt reicht bis hinter StatusBar und NavigationsBar.
        // AppBarLayout.fitsSystemWindows="true" im Layout übernimmt den StatusBar-Abstand
        // für die Toolbar automatisch; Fragments behandeln nur noch Bottom-Insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // MaterialToolbar aus dem Layout als ActionBar registrieren –
        // damit funktionieren setupActionBarWithNavController und das Menü-System
        // der Fragments unverändert weiter.
        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        navController = navHost.navController

        if (TokenStore(this).isConfigured) {
            navController.navigate(R.id.action_token_to_chat)
        }

        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
