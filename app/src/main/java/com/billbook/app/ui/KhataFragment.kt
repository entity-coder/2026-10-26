package com.billbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.data.BillDatabase
import com.billbook.app.data.BillRepository
import com.billbook.app.data.Customer
import com.billbook.app.databinding.FragmentKhataBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Khata tab: per-customer dues, computed live (never stored). Tapping a
 * customer records a payment or shows payment history.
 */
class KhataFragment : Fragment() {

    private var _binding: FragmentKhataBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentKhataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val repo = BillRepository(db)
        binding.recyclerCustomers.layoutManager = LinearLayoutManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.customerDao().observeAll().collectLatest { customers ->
                // Dues are computed per customer on every customers change.
                val dues = mutableMapOf<Long, Double>()
                for (customer in customers) {
                    dues[customer.id] = repo.customerDue(customer.id)
                }
                binding.recyclerCustomers.adapter = CustomerAdapter(
                    dues = dues,
                    onRecordPayment = { customer ->
                        PaymentDialogFragment.newInstance(customer)
                            .show(parentFragmentManager, "record_payment")
                    },
                    onHistory = { customer ->
                        PaymentHistoryDialogFragment.newInstance(customer)
                            .show(parentFragmentManager, "payment_history")
                    },
                ).apply { submitList(customers) }
                binding.textEmptyKhata.visibility =
                    if (customers.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
