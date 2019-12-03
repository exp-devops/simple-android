package org.simple.clinic.summary.bloodpressures

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.cardview.widget.CardView
import org.simple.clinic.R

class BloodPressurePlaceholderItemView(
    context: Context,
    attributeSet: AttributeSet
) : CardView(context, attributeSet) {

  init {
    LayoutInflater.from(context).inflate(R.layout.patientsummary_bpplaceholderitem_content, this, true)
  }
}
