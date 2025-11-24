package at.plankt0n.streamplay

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import at.plankt0n.streamplay.data.Tool
import at.plankt0n.streamplay.data.ToolsRepository
import at.plankt0n.streamplay.helper.ViewIdGenerator
import at.plankt0n.streamplay.ui.ToolDetailFragment
import at.plankt0n.streamplay.ui.ToolFormFragment
import at.plankt0n.streamplay.ui.ToolListFragment

class ToolsActivity : AppCompatActivity(),
    ToolListFragment.ToolListHost,
    ToolFormFragment.ToolFormHost,
    ToolDetailFragment.ToolDetailHost {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var repository: ToolsRepository
    private lateinit var toggle: ActionBarDrawerToggle
    private val openToolItems = mutableMapOf<Int, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        repository = ToolsRepository(this)
        drawerLayout = findViewById(R.id.tools_drawer)
        navigationView = findViewById(R.id.tools_navigation)

        setSupportActionBar(findViewById(R.id.tools_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.menu_tools_list)

        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { item ->
            handleNavigation(item)
            true
        }

        if (savedInstanceState == null) {
            showToolList()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (toggle.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    private fun handleNavigation(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_tools_list -> {
                supportActionBar?.title = getString(R.string.menu_tools_list)
                showToolList()
            }
            R.id.menu_tools_add -> {
                supportActionBar?.title = getString(R.string.menu_tools_add)
                showAddTool()
            }
            else -> {
                val toolId = openToolItems[item.itemId]
                if (toolId != null) {
                    openTool(toolId)
                }
            }
        }
        drawerLayout.closeDrawers()
    }

    private fun showToolList() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.tools_fragment_container, ToolListFragment())
            .commit()
    }

    private fun showAddTool() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.tools_fragment_container, ToolFormFragment())
            .commit()
    }

    private fun openTool(id: Long) {
        val fragment = ToolDetailFragment.newInstance(id)
        supportActionBar?.title = getString(R.string.tools_detail_nav_title, id)
        supportFragmentManager.beginTransaction()
            .replace(R.id.tools_fragment_container, fragment)
            .commit()
    }

    override fun onToolSelected(id: Long) {
        val tool = repository.getTool(id)
        if (tool == null) {
            Toast.makeText(this, R.string.toast_tool_missing, Toast.LENGTH_SHORT).show()
            return
        }
        addToolToMenu(tool)
        openTool(id)
    }

    override fun onAddToolRequested() {
        navigationView.setCheckedItem(R.id.menu_tools_add)
        showAddTool()
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onToolCreated(id: Long) {
        val tool = repository.getTool(id)
        if (tool != null) {
            addToolToMenu(tool)
            openTool(id)
        }
    }

    override fun onToolUpdated(tool: Tool) {
        val entry = openToolItems.entries.find { it.value == tool.id }
        if (entry != null) {
            navigationView.menu.findItem(entry.key)?.title = tool.orderNumber
        }
    }

    private fun addToolToMenu(tool: Tool) {
        val existingEntry = openToolItems.entries.find { it.value == tool.id }
        if (existingEntry != null) {
            navigationView.menu.findItem(existingEntry.key)?.title = tool.orderNumber
            navigationView.setCheckedItem(existingEntry.key)
            return
        }
        val menuId = ViewIdGenerator.generate()
        navigationView.menu.add(R.id.open_tools_group, menuId, MenuItem.SHOW_AS_ACTION_NEVER, tool.orderNumber)
        openToolItems[menuId] = tool.id
        navigationView.setCheckedItem(menuId)
    }
}
