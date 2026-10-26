package com.billbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.data.BillDatabase
import com.billbook.app.databinding.FragmentBillsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Printed bills, newest first; tap one for its detail screen. */
class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val adapter = BillAdapter { bill ->
            startActivity(
                Intent(requireContext(), BillDetailActivity::class.java).apply {
                    putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.id)
                }
            )
        }
        binding.recyclerBills.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBills.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            db.billDao().observePrinted().collectLatest { bills ->
                adapter.submitList(bills)
                binding.textEmptyBills.visibility =
                    if (bills.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
