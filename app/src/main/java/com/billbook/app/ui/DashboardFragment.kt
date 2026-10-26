package com.billbook.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.R
import com.billbook.app.data.BillDatabase
import com.billbook.app.data.BillRepository
import com.billbook.app.data.formatMoney
import com.billbook.app.data.todayString
import com.billbook.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Today's sales, total pending dues, low-stock alerts, shop profile edit. */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val repo = BillRepository(db)
        // Low-stock rows reuse the catalog adapter, so Edit / Stock In work here too.
        val lowStockAdapter = ProductAdapter(
            onEdit = { product ->
                ProductDialogFragment.newInstance(product)
                    .show(parentFragmentManager, "edit_product")
            },
            onStockIn = { product ->
                StockInDialogFragment.newInstance(product)
                    .show(parentFragmentManager, "stock_in")
            },
            onDelete = { product ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.productDao().delete(product)
                }
            },
        )
        binding.recyclerLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLowStock.adapter = lowStockAdapter

        binding.buttonEditShop.setOnClickListener {
            ShopProfileDialogFragment().show(parentFragmentManager, "shop_profile")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.billDao().observePrintedTotalForDate(todayString()).collectLatest { total ->
                binding.textTodaySales.text = formatMoney(total)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            db.shopProfileDao().observe().collectLatest { profile ->
                binding.textShopName.text = profile?.name?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.shop_not_set)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            db.productDao().observeLowStock().collectLatest { products ->
                lowStockAdapter.submitList(products)
                binding.textLowStockEmpty.visibility =
                    if (products.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        // Total dues = sum over all customers of computed dues.
        viewLifecycleOwner.lifecycleScope.launch {
            db.customerDao().observeAll().collectLatest { customers ->
                var total = 0.0
                for (customer in customers) {
                    total += repo.customerDue(customer.id)
                }
                binding.textTotalDues.text = formatMoney(total)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
