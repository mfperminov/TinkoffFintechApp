package com.example.tinkofffintechapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var apiService: CurrencyApiService
    private lateinit var cacheControl: CacheControl
    private var queryDisposable: Disposable? = null
    private var networkDisposable: Disposable? = null
    private var check = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheControl = CacheControl(this)
        setContentView(R.layout.activity_main)
        observeInternetConnection(cacheControl)
        apiService = CurrencyApiService.create(cacheControl.okHttpClient)
        setupSpinner(R.id.spinner_in)
        setupSpinner(R.id.spinner_out)
        setupEditText(et_in)
        setupEditText(et_out)
    }

    override fun onStart() {
        super.onStart()
        networkDisposable = observeInternetConnection(cacheControl).observeOn(AndroidSchedulers.mainThread())
            .subscribe { hasNetwork ->
                if (hasNetwork) {
                    // убираем предпреждение и конвертируем последний ввод помощью интернета
                    network_warning.visibility = View.GONE
                    if (++check > 1) {
                        // убираем фокус со всех форм, чтобы слушатель ввода формы не стригерился
                        currentFocus?.clearFocus()
                        if (checkInputIsDouble(
                                et_in.text.toString(),
                                til_in
                            )
                        ) {
                            til_out.error = null
                            val currencyIn = spinner_in.selectedItem as String
                            val currencyOut = spinner_out.selectedItem as String
                            val currenciesQuery = String.format(QUERY_TEMPLATE, currencyIn, currencyOut)
                            val quantityToConvert = et_in.text.toString().replace(",", ".").toDouble()
                            makeQuery(currenciesQuery, quantityToConvert, et_out)
                        }
                    }
                } else network_warning.visibility = View.VISIBLE
            }
    }

    override fun onStop() {
        super.onStop()
        networkDisposable?.dispose()
    }

    private fun setupEditText(textInputEditText: TextInputEditText) {

        textInputEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(newText: CharSequence?, p1: Int, p2: Int, p3: Int) {
                // проверить фокус, чтобы не попасть в круг "смена текста в форме - триггер слушателя"
                if (currentFocus == textInputEditText) {
                    val isConvertFlowStraight = textInputEditText == et_in
                    val targetEditText = if (isConvertFlowStraight) et_out else et_in
                    val currencyIn = (if (isConvertFlowStraight) spinner_in else spinner_out).selectedItem as String
                    val currencyOut = (if (isConvertFlowStraight) spinner_out else spinner_in).selectedItem as String
                    val initTextInputLayout = if (isConvertFlowStraight) til_in else til_out
                    val targetTextInputLayout = if (isConvertFlowStraight) til_out else til_in
                    if (checkInputIsDouble(newText, initTextInputLayout)) {
                        targetTextInputLayout.error = null
                        val currenciesQuery = String.format(QUERY_TEMPLATE, currencyIn, currencyOut)
                        val quantityToConvert = newText.toString().replace(",", ".").toDouble()
                        makeQuery(currenciesQuery, quantityToConvert, targetEditText)
                    }
                }
            }
        })

    }

    private fun setupSpinner(idSpinner: Int) {
        val spinner: Spinner = findViewById(idSpinner)
        ArrayAdapter.createFromResource(
            this,
            R.array.currencies_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.setSelection(0, false)
            // во второй спиннер ставим вторую по порядку в валюту, чтобы не было одинакового выбора
            if (idSpinner == R.id.spinner_out)
                spinner.setSelection(if (adapter.getItem(1) != null) 1 else 0, false)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                currentFocus?.clearFocus()
                val currencyIn = (if (idSpinner == R.id.spinner_in) spinner_out else spinner_in).selectedItem as String
                val currencyOut = parent?.getItemAtPosition(position) as String
                // определяем в какую сторону конвертируем  - берем значение из initEditText и должны ввести результат в targetEditText
                // TextInputLayout для отображения возможных ошибок ввода
                val initEditText = if (idSpinner == R.id.spinner_in) et_out else et_in
                val initTextInputLayout = if (initEditText == et_in) til_in else til_out
                val targetEditText = if (initEditText == et_in) et_out else et_in
                val targetTextInputLayout = if (initEditText == et_in) til_out else til_in
                if (checkInputIsDouble(initEditText.text.toString(), initTextInputLayout)) {
                    targetTextInputLayout.error = null
                    val currenciesQuery = String.format(QUERY_TEMPLATE, currencyIn, currencyOut)
                    val quantityToConvert = initEditText.text.toString().replace(",", ".").toDouble()
                    makeQuery(currenciesQuery, quantityToConvert, targetEditText)
                }
            }

        }
    }

    private fun checkInputIsDouble(
        text: CharSequence?,
        textInputLayout: TextInputLayout
    ): Boolean {
        return try {
            // заменяем запятую на точку - избегаем бага в локалях где десятичный знак запятая
            // и пользователь выбирает конвертацию предыдущего результата
            text.toString().replace(",", ".").toDouble()
            textInputLayout.error = null
            true
        } catch (e: Exception) {
            textInputLayout.error = getString(R.string.number_incorrect_error)
            // очищаем противоположный EditText если в текущем ошибка
            if (textInputLayout == til_in) et_out.setText("", TextView.BufferType.NORMAL) else et_in.setText(
                "",
                TextView.BufferType.NORMAL
            )
            false
        }

    }

    private fun makeQuery(
        currencies: String,
        quantityToConvert: Double,
        targetEditText: TextInputEditText
    ) {
        indeterminateBar.visibility = View.VISIBLE
        queryDisposable = apiService.convert(currencies).observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({ convertResult ->
                indeterminateBar.visibility = View.GONE
                assert(convertResult.containsKey(currencies))
                val multiplier = convertResult[currencies]!!.result
                val totalResult = multiplier * quantityToConvert
                val formattedResult = String.format("%.2f", totalResult)
                targetEditText.setText(formattedResult)

            }, { error -> indeterminateBar.visibility = View.GONE; showError(getString(R.string.something_wrong)) })
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun observeInternetConnection(cacheControl: CacheControl): Observable<Boolean> {
        return Observable.interval(
            0, 1, TimeUnit.SECONDS,
            Schedulers.io()
        ).map { cacheControl.hasNetwork(this) }
            .distinctUntilChanged()
    }

    companion object {
        const val QUERY_TEMPLATE = "%s_%s"
    }
}
