package com.example.skillsync.ui.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillsync.R
import com.example.skillsync.data.api.RetrofitClient
import com.example.skillsync.theme.AuroraBackground
import com.example.skillsync.theme.NumericStyle
import com.example.skillsync.theme.Radii
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.editorialRule
import com.example.skillsync.theme.skill
import com.example.skillsync.ui.components.SectionHeader
import com.example.skillsync.ui.components.rows
import com.example.skillsync.ui.components.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MyScheduleViewModel : ViewModel() {
    private val _data = MutableStateFlow<Map<String, Any>?>(null)
    val data: StateFlow<Map<String, Any>?> = _data
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(email: String) = viewModelScope.launch {
        _loading.value = true
        try { _data.value = RetrofitClient.instance.trainerCalendar(email) } catch (_: Exception) {}
        _loading.value = false
    }
}

/**
 * A personal schedule for anyone who also delivers — managers and assistant
 * managers included. Own assignments, current and upcoming, and the shift bands
 * RMS has you marked off for. Reached from the profile menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScheduleScreen(
    email: String,
    onOpenPractice: () -> Unit,
    onBack: () -> Unit,
    vm: MyScheduleViewModel = viewModel(),
) {
    val sk = MaterialTheme.skill
    LaunchedEffect(email) { vm.load(email) }
    val data by vm.data.collectAsState()
    val loading by vm.loading.collectAsState()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("My schedule", style = MaterialTheme.typography.headlineSmall, color = sk.bodyText)
                            Text("YOUR OWN DELIVERY · LEAVE BANDS", style = MaterialTheme.typography.labelSmall, color = sk.sky)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), "Back", tint = sk.ice)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            if (loading && data == null) {
                Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = sk.brand)
                }
                return@Scaffold
            }
            val d = data ?: emptyMap()
            val current = d.rows("current")
            val upcoming = d.rows("upcoming")
            val past = d.rows("past")
            val off = (d["off_bands"] as? Map<*, *>) ?: emptyMap<Any, Any>()

            LazyColumn(
                Modifier.padding(pad).fillMaxSize().padding(horizontal = Space.xl),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.md, bottom = Space.xxl),
            ) {
                item {
                    SectionHeader("Where you are", conclusion = when {
                        current.isNotEmpty() -> "Delivering ${current.first().str("course")} now."
                        upcoming.isNotEmpty() -> "${upcoming.size} batch${if (upcoming.size == 1) "" else "es"} ahead."
                        else -> "Clear diary."
                    })
                }
                if (current.isNotEmpty()) {
                    item { Lbl("DELIVERING NOW") }
                    items(current) { Assign(it) }
                }
                if (upcoming.isNotEmpty()) {
                    item { Lbl("UPCOMING") }
                    items(upcoming) { Assign(it) }
                }
                if (off.isNotEmpty()) {
                    item {
                        SectionHeader("Shift bands you're marked off",
                            conclusion = "Batches in these bands skip you. Ask RMS to correct if wrong.")
                    }
                    items(off.entries.toList()) { (k, _) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule()) {
                            Text(k.toString().replace("_", " ").uppercase(),
                                style = MaterialTheme.typography.titleSmall, color = sk.warn)
                        }
                    }
                }
                if (past.isNotEmpty()) {
                    item { Lbl("RECENTLY DELIVERED") }
                    items(past.reversed()) { Assign(it, dim = true) }
                }
                item {
                    Spacer(Modifier.height(Space.xl))
                    Button(
                        onClick = onOpenPractice,
                        colors = ButtonDefaults.buttonColors(containerColor = sk.brand),
                        shape = RoundedCornerShape(Radii.chip),
                    ) { Text("My learner feedback & recordings  →") }
                }
            }
        }
    }
}

@Composable
private fun Assign(a: Map<*, *>, dim: Boolean = false) {
    val sk = MaterialTheme.skill
    Column(Modifier.fillMaxWidth().padding(vertical = Space.md).editorialRule()) {
        Text(a.str("course"), style = MaterialTheme.typography.titleMedium,
            color = if (dim) sk.subText else sk.bodyText)
        Text(
            listOf(a.str("start_date"),
                a.str("end_date").let { if (it.isNotBlank()) "→ $it" else "" },
                a.str("mode"), a.str("location"))
                .filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = sk.subText,
        )
    }
}

@Composable
private fun Lbl(t: String) = Text(
    t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.skill.labelText,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.lg, bottom = Space.xs),
)
