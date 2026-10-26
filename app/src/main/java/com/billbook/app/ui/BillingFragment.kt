package com.billbook.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.R
import com.billbook.app.data.Bill
import com.billbook.app.data.BillDatabase
import com.billbook.app.data.BillItem
import com.billbook.app.data.BillRepository
import com.billbook.app.data.PaymentType
import com.billbook.app.data.PricedLine
import com.billbook.app.data.computeBillTotals
import com.billbook.app.data.formatMoney
import com.billbook.app.data.lineAmount
import com.billbook.app.data.round2
import com.billbook.app.data.todayString
import com.billbook.app.databinding.FragmentBillingBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Billing tab. A bill can only be started after a customer name and a payment
 * type are given. Multiple DRAFT bills are kept at once and switched with
 * chips. Saving a draft never touches stock — only printing does.
 */
class BillingFragment : Fragment() {

    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: BillRepository
    private var selectedBillId: Long? = null
    private var currentBill: Bill? = null
    private var currentLines: List<BillItem> = emptyList()
    private var updatingDiscountText = false
    private var linesJob: kotlinx.coroutines.Job? = null
    private var linesJobBillId: Long? = null

    /** (Re)start observing one draft's lines; the old job is cancelled first. */
    private fun startLinesObservation(db: BillDatabase, billId: Long, adapter: BillItemAdapter) {
        if (linesJobBillId == billId && linesJob?.isActive == true) return
        linesJob?.cancel()
        linesJobBillId = billId
        linesJob = viewLifecycleOwner.lifecycleScope.launch {
            db.billItemDao().observeForBill(billId).collectLatest { lines ->
                currentLines = lines
                adapter.submitList(lines)
                renderTotals()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = BillDatabase.get(requireContext())
        repo = BillRepository(db)

        // Payment type spinner: first row is a "select" placeholder.
        val paymentOptions = listOf(
            getString(R.string.select_payment),
            PaymentType.CASH, PaymentType.UPI, PaymentType.CREDIT,
        )
        binding.spinnerPayment.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            paymentOptions,
        )

        // Customer autocomplete from existing names (new names still allowed).
        viewLifecycleOwner.lifecycleScope.launch {
            db.customerDao().observeAll().collectLatest { customers ->
                binding.autoCustomer.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        customers.map { it.name },
                    )
                )
            }
        }

        val lineAdapter = BillItemAdapter(
            onEdit = { item -> showLineDialog(item) },
            onDelete = { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.billItemDao().delete(item)
                }
            },
        )
        binding.recyclerBillItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBillItems.adapter = lineAdapter

        binding.buttonNewBill.setOnClickListener {
            val name = binding.autoCustomer.text.toString().trim()
            val payment = binding.spinnerPayment.selectedItem as String
            if (name.isEmpty() || payment == getString(R.string.select_payment)) {
                Toast.makeText(
                    requireContext(),
                    R.string.customer_and_payment_required,
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                // A new customer name creates the customer row (khata rule).
                val customer = repo.getOrCreateCustomer(name)
                val id = db.billDao().insert(
                    Bill(
                        customerId = customer.id,
                        customerName = customer.name,
                        paymentType = payment,
                        createdDate = todayString(),
                    )
                )
                selectedBillId = id
                selectBill(db, id, lineAdapter)
            }
        }

        binding.buttonAddItem.setOnClickListener {
            val billId = selectedBillId ?: return@setOnClickListener
            ProductPickDialogFragment.newInstance(billId)
                .show(parentFragmentManager, "pick_product")
        }

        binding.editDiscount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingDiscountText) return
                val bill = currentBill ?: return
                val pct = s.toString().toDoubleOrNull() ?: 0.0
                currentBill = bill.copy(discountPercent = pct)
                viewLifecycleOwner.lifecycleScope.launch {
                    db.billDao().update(bill.copy(discountPercent = pct))
                }
                renderTotals()
            }
        })

        binding.buttonPrint.setOnClickListener {
            val bill = currentBill ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                if (currentLines.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.bill_needs_items, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                when (val result = repo.printBill(bill.id)) {
                    is BillRepository.PrintResult.Success -> {
                        Toast.makeText(requireContext(), R.string.bill_printed, Toast.LENGTH_SHORT).show()
                        selectedBillId = null
                    }
                    is BillRepository.PrintResult.InsufficientStock -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.insufficient_stock_title)
                            .setMessage(result.shortProducts.joinToString("\n"))
                            .setPositiveButton(R.string.cancel, null)
                            .show()
                    }
                }
            }
        }

        binding.buttonDeleteDraft.setOnClickListener {
            val bill = currentBill ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_draft_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        db.billItemDao().deleteForBill(bill.id)
                        db.billDao().delete(bill)
                        selectedBillId = null
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Draft chips: one per open draft, switchable at any time. The chips
        // list is the only drafts collector; selection changes bind directly
        // via selectBill() (the drafts Flow only emits on DB changes).
        viewLifecycleOwner.lifecycleScope.launch {
            db.billDao().observeDrafts().collectLatest { drafts ->
                binding.chipGroupDrafts.removeAllViews()
                for (draft in drafts) {
                    val chip = Chip(requireContext()).apply {
                        text = "Draft #${draft.id} — ${draft.customerName}"
                        isCheckable = true
                        isChecked = draft.id == selectedBillId
                        setOnClickListener {
                            selectedBillId = draft.id
                            selectBill(db, draft.id, lineAdapter)
                        }
                    }
                    binding.chipGroupDrafts.addView(chip)
                }
                // If the selected draft was printed/deleted, drop the selection.
                if (selectedBillId != null && drafts.none { it.id == selectedBillId }) {
                    selectedBillId = null
                    linesJob?.cancel()
                    linesJobBillId = null
                    currentBill = null
                    currentLines = emptyList()
                    lineAdapter.submitList(emptyList())
                    binding.textDraftLabel.text = getString(R.string.no_draft_selected)
                    renderTotals()
                }
            }
        }
    }

    /** Bind the billing screen to one draft: label, discount field, lines. */
    private fun selectBill(db: BillDatabase, billId: Long, adapter: BillItemAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bill = db.billDao().getById(billId) ?: return@launch
            currentBill = bill
            binding.textDraftLabel.text =
                "Draft #${bill.id} — ${bill.customerName} (${bill.paymentType})"
            if (!binding.editDiscount.hasFocus()) {
                updatingDiscountText = true
                binding.editDiscount.setText(
                    if (bill.discountPercent == 0.0) "" else bill.discountPercent.toString()
                )
                updatingDiscountText = false
            }
            startLinesObservation(db, billId, adapter)
        }
    }

    /** Recompute totals from the current lines + discount, via Money.kt only. */
    private fun renderTotals() {
        val bill = currentBill
        val totals = computeBillTotals(
            currentLines.map { PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent) },
            bill?.discountPercent ?: 0.0,
        )
        binding.textSubtotal.text = getString(R.string.subtotal_format, formatMoney(totals.subtotal))
        binding.textDiscountAmount.text =
            getString(R.string.discount_amount_format, formatMoney(totals.discountAmount))
        binding.textTaxable.text = getString(R.string.taxable_format, formatMoney(totals.taxable))
        binding.textCgst.text = getString(R.string.cgst_format, formatMoney(totals.cgst))
        binding.textSgst.text = getString(R.string.sgst_format, formatMoney(totals.sgst))
        binding.textGrandTotal.text =
            getString(R.string.grand_total_format, formatMoney(totals.grandTotal))
    }

    /** Edit a line's qty (price kind stays as chosen when the line was added). */
    private fun showLineDialog(item: BillItem) {
        val editQty = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(item.qty.toString())
        }
        AlertDialog.Builder(requireContext())
            .setTitle(item.productName)
            .setView(editQty)
            .setPositiveButton(R.string.save) { _, _ ->
                val qty = editQty.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    BillDatabase.get(requireContext()).billItemDao()
                        .update(item.copy(qty = round2(qty)))
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
