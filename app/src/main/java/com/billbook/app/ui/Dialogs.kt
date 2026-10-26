package com.billbook.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.R
import com.billbook.app.data.BillDatabase
import com.billbook.app.data.BillItem
import com.billbook.app.data.Customer
import com.billbook.app.data.Payment
import com.billbook.app.data.PaymentType
import com.billbook.app.data.Product
import com.billbook.app.data.ShopProfile
import com.billbook.app.data.formatMoney
import com.billbook.app.data.round2
import com.billbook.app.data.todayString
import com.billbook.app.databinding.DialogPaymentBinding
import com.billbook.app.databinding.DialogPaymentHistoryBinding
import com.billbook.app.databinding.DialogProductBinding
import com.billbook.app.databinding.DialogProductPickBinding
import com.billbook.app.databinding.DialogShopProfileBinding
import com.billbook.app.databinding.DialogStockInBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Create / edit a product. Editing stock here does NOT log a movement — use Stock In for that. */
class ProductDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        fun newInstance(product: Product?): ProductDialogFragment =
            ProductDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PRODUCT_ID, product?.id ?: -1L)
                }
            }
    }

    private var _binding: DialogProductBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val productId = requireArguments().getLong(ARG_PRODUCT_ID)
        var existing: Product? = null
        if (productId != -1L) {
            viewLifecycleOwner.lifecycleScope.launch {
                existing = db.productDao().getById(productId)
                existing?.let { p ->
                    binding.editName.setText(p.name)
                    binding.editUnit.setText(p.unit)
                    binding.editWholesale.setText(p.wholesalePrice.toString())
                    binding.editRetail.setText(p.retailPrice.toString())
                    binding.editGst.setText(p.gstPercent.toString())
                    binding.editStock.setText(p.stock.toString())
                    binding.editThreshold.setText(p.lowStockThreshold.toString())
                }
            }
        }
        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), R.string.product_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val product = Product(
                id = existing?.id ?: 0,
                name = name,
                unit = binding.editUnit.text.toString().ifBlank { "pcs" },
                wholesalePrice = binding.editWholesale.text.toString().toDoubleOrNull() ?: 0.0,
                retailPrice = binding.editRetail.text.toString().toDoubleOrNull() ?: 0.0,
                gstPercent = binding.editGst.text.toString().toDoubleOrNull() ?: 0.0,
                // When editing, the stock field is a plain correction; the
                // audited path for adding stock is Stock In (logged).
                stock = binding.editStock.text.toString().toDoubleOrNull()
                    ?: existing?.stock ?: 0.0,
                lowStockThreshold = binding.editThreshold.text.toString().toDoubleOrNull() ?: 0.0,
            )
            viewLifecycleOwner.lifecycleScope.launch {
                if (existing == null) db.productDao().insert(product)
                else db.productDao().update(product)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Stock In: adds stock and logs a stock movement (audited path). */
class StockInDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        fun newInstance(product: Product): StockInDialogFragment =
            StockInDialogFragment().apply {
                arguments = Bundle().apply { putLong(ARG_PRODUCT_ID, product.id) }
            }
    }

    private var _binding: DialogStockInBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogStockInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val productId = requireArguments().getLong(ARG_PRODUCT_ID)
        viewLifecycleOwner.lifecycleScope.launch {
            val product = db.productDao().getById(productId) ?: return@launch
            binding.textStockInProduct.text =
                "${product.name} — current stock ${product.stock} ${product.unit}"
            binding.buttonSave.setOnClickListener {
                val qty = binding.editQty.text.toString().toDoubleOrNull() ?: 0.0
                if (qty <= 0) return@setOnClickListener
                viewLifecycleOwner.lifecycleScope.launch {
                    // Re-read: stock may have changed since the dialog opened.
                    val fresh = db.productDao().getById(productId) ?: return@launch
                    com.billbook.app.data.BillRepository(db).stockIn(fresh, round2(qty))
                    Toast.makeText(requireContext(), R.string.stock_in_saved, Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Single-row shop profile, edited from the Dashboard and shown on bills/PDFs. */
class ShopProfileDialogFragment : DialogFragment() {

    private var _binding: DialogShopProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogShopProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = db.shopProfileDao().get()
            profile?.let {
                binding.editShopName.setText(it.name)
                binding.editShopAddress.setText(it.address)
                binding.editShopGstin.setText(it.gstin)
                binding.editShopPhone.setText(it.phone)
            }
            binding.buttonSave.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.shopProfileDao().save(
                        ShopProfile(
                            name = binding.editShopName.text.toString(),
                            address = binding.editShopAddress.text.toString(),
                            gstin = binding.editShopGstin.text.toString(),
                            phone = binding.editShopPhone.text.toString(),
                        )
                    )
                    Toast.makeText(requireContext(), R.string.shop_profile_saved, Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Record a khata payment (Cash/UPI/Bank). Dues recompute automatically. */
class PaymentDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CUSTOMER_ID = "customer_id"
        fun newInstance(customer: Customer): PaymentDialogFragment =
            PaymentDialogFragment().apply {
                arguments = Bundle().apply { putLong(ARG_CUSTOMER_ID, customer.id) }
            }
    }

    private var _binding: DialogPaymentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val customerId = requireArguments().getLong(ARG_CUSTOMER_ID)
        binding.spinnerMethod.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(PaymentType.CASH, PaymentType.UPI, PaymentType.BANK),
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val customer = db.customerDao().getById(customerId) ?: return@launch
            binding.textPaymentCustomer.text = customer.name
            binding.buttonSave.setOnClickListener {
                val amount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0) return@setOnClickListener
                viewLifecycleOwner.lifecycleScope.launch {
                    db.paymentDao().insert(
                        Payment(
                            customerId = customer.id,
                            customerName = customer.name,
                            amount = round2(amount),
                            method = binding.spinnerMethod.selectedItem as String,
                            date = todayString(),
                        )
                    )
                    Toast.makeText(requireContext(), R.string.payment_saved, Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Payment history for one customer, newest first. */
class PaymentHistoryDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CUSTOMER_ID = "customer_id"
        fun newInstance(customer: Customer): PaymentHistoryDialogFragment =
            PaymentHistoryDialogFragment().apply {
                arguments = Bundle().apply { putLong(ARG_CUSTOMER_ID, customer.id) }
            }
    }

    private var _binding: DialogPaymentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogPaymentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val customerId = requireArguments().getLong(ARG_CUSTOMER_ID)
        val adapter = PaymentAdapter()
        binding.recyclerPayments.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerPayments.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            db.paymentDao().observeForCustomer(customerId).collectLatest { payments ->
                adapter.submitList(payments)
                binding.textHistoryEmpty.visibility =
                    if (payments.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Product picker for a draft bill. Tapping a product asks for qty and the
 * price kind (wholesale or retail) for that line, then inserts the line.
 */
class ProductPickDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_BILL_ID = "bill_id"
        fun newInstance(billId: Long): ProductPickDialogFragment =
            ProductPickDialogFragment().apply {
                arguments = Bundle().apply { putLong(ARG_BILL_ID, billId) }
            }
    }

    private var _binding: DialogProductPickBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogProductPickBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        val billId = requireArguments().getLong(ARG_BILL_ID)
        val adapter = ProductPickAdapter { product -> showQtyDialog(db, billId, product) }
        binding.recyclerPick.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerPick.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            // Snapshot list: the picker closes on pick, so a Flow is unnecessary.
            adapter.submitList(db.productDao().observeAll().first())
        }
    }

    private fun showQtyDialog(db: BillDatabase, billId: Long, product: Product) {
        // Qty + price-kind radios, built in code (no extra layout file needed).
        val qtyEdit = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.qty_label)
            setText("1")
        }
        val radios = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(requireContext()).apply {
                text = "${getString(R.string.price_kind_wholesale)} — ${formatMoney(product.wholesalePrice)}"
                id = View.generateViewId()
                isChecked = true
                tag = "Wholesale"
            })
            addView(RadioButton(requireContext()).apply {
                text = "${getString(R.string.price_kind_retail)} — ${formatMoney(product.retailPrice)}"
                id = View.generateViewId()
                tag = "Retail"
            })
        }
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            addView(qtyEdit)
            addView(radios)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val qty = qtyEdit.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (qty <= 0) return@setPositiveButton
                val checked = radios.findViewById<RadioButton>(radios.checkedRadioButtonId)
                val wholesale = checked?.tag == "Wholesale"
                viewLifecycleOwner.lifecycleScope.launch {
                    db.billItemDao().insert(
                        BillItem(
                            billId = billId,
                            productId = product.id,
                            productName = product.name,
                            unit = product.unit,
                            qty = round2(qty),
                            unitPrice = if (wholesale) product.wholesalePrice else product.retailPrice,
                            priceKind = if (wholesale) "Wholesale" else "Retail",
                            gstPercent = product.gstPercent,
                        )
                    )
                    dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
