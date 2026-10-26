package com.billbook.app.ui

import android.app.AlertDialog
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
import com.billbook.app.data.Product
import com.billbook.app.databinding.FragmentProductsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Product catalog: CRUD plus Stock In (which logs a stock movement). */
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val adapter = ProductAdapter(
            onEdit = { product ->
                ProductDialogFragment.newInstance(product)
                    .show(parentFragmentManager, "edit_product")
            },
            onStockIn = { product ->
                StockInDialogFragment.newInstance(product)
                    .show(parentFragmentManager, "stock_in")
            },
            onDelete = { product ->
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.delete_product_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            db.productDao().delete(product)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
        )
        binding.recyclerProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProducts.adapter = adapter
        binding.fabAddProduct.setOnClickListener {
            ProductDialogFragment.newInstance(null)
                .show(parentFragmentManager, "add_product")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            db.productDao().observeAll().collectLatest { products ->
                adapter.submitList(products)
                binding.textEmptyProducts.visibility =
                    if (products.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
