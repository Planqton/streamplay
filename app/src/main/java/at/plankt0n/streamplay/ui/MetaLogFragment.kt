package at.plankt0n.streamplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.MetaLogAdapter
import at.plankt0n.streamplay.helper.MetaLogHelper

class MetaLogFragment : Fragment() {
    private lateinit var adapter: MetaLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_meta_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backButton = view.findViewById<ImageButton>(R.id.arrow_back)
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = MetaLogAdapter { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerMetaLog)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<Button>(R.id.buttonClearLogs).setOnClickListener {
            MetaLogHelper.clear(requireContext())
            adapter.setItems(emptyList())
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.setItems(MetaLogHelper.getLogs(requireContext()))
    }
}
