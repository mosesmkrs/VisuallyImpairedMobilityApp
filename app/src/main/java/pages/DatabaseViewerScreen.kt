package pages

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import com.example.newapp.SQL.PC.PrimaryContact
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.SecondaryContact
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel
import com.example.newapp.SQL.users.Users

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseViewerScreen(navController: NavController, tts: TextToSpeech) {
    val context = LocalContext.current
    
    // State for data
    var users by remember { mutableStateOf<List<Users>>(emptyList()) }
    var primaryContacts by remember { mutableStateOf<List<PrimaryContact>>(emptyList()) }
    var secondaryContacts by remember { mutableStateOf<List<SecondaryContact>>(emptyList()) }
    
    // Set up observers for LiveData
    val lifecycleOwner = context as LifecycleOwner
    val userObserver = remember { Observer<List<Users>> { users = it } }
    val primaryContactObserver = remember { Observer<List<PrimaryContact>> { primaryContacts = it } }
    val secondaryContactObserver = remember { Observer<List<SecondaryContact>> { secondaryContacts = it } }
    
    // Get ViewModels
    val userViewModel = ViewModelProvider(context as ViewModelStoreOwner).get(UserViewModel::class.java)
    val primaryContactViewModel = ViewModelProvider(context as ViewModelStoreOwner).get(pCViewModel::class.java)
    val secondaryContactViewModel = ViewModelProvider(context as ViewModelStoreOwner).get(sCViewModel::class.java)
    
    // Set up observers
    DisposableEffect(lifecycleOwner) {
        userViewModel._allUsers.observe(lifecycleOwner, userObserver)
        primaryContactViewModel._allContacts.observe(lifecycleOwner, primaryContactObserver)
        secondaryContactViewModel._allContacts.observe(lifecycleOwner, secondaryContactObserver)
        
        onDispose {
            userViewModel._allUsers.removeObserver(userObserver)
            primaryContactViewModel._allContacts.removeObserver(primaryContactObserver)
            secondaryContactViewModel._allContacts.removeObserver(secondaryContactObserver)
        }
    }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Users", "Primary Contacts", "Secondary Contacts")
    
    LaunchedEffect(Unit) {
        tts.speak("Database Viewer Screen. Showing ${tabs[selectedTab]} table.", TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Database Viewer") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { 
                        selectedTab = index 
                        tts.speak("Showing ${tabs[index]} table.", TextToSpeech.QUEUE_FLUSH, null, null)
                    },
                    text = { Text(title) }
                )
            }
        }
        
        when (selectedTab) {
            0 -> UsersTable(users, tts)
            1 -> PrimaryContactsTable(primaryContacts, tts)
            2 -> SecondaryContactsTable(secondaryContacts, tts)
        }
    }
}

@Composable
fun UsersTable(users: List<Users>, tts: TextToSpeech) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
                Text("ID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Text("Name", modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold)
                Text("Email", modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold)
            }
        }
        
        items(users) { user ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .clickable {
                        tts.speak(
                            "User ID: ${user.userID}, Name: ${user.name}, Email: ${user.email}",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                        )
                    }
            ) {
                Text(user.userID.toString(), modifier = Modifier.weight(0.2f))
                Text(user.name ?: "N/A", modifier = Modifier.weight(0.4f))
                Text(user.email ?: "N/A", modifier = Modifier.weight(0.4f))
            }
            Divider()
        }
    }
}

@Composable
fun PrimaryContactsTable(contacts: List<PrimaryContact>, tts: TextToSpeech) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
                Text("ID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Text("User ID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Text("Name", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                Text("Phone", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
            }
        }
        
        items(contacts) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .clickable { tts.speak("Primary Contact ID: ${contact.pcID}, User ID: ${contact.userID}, Name: ${contact.contactname}, Phone: ${contact.contactnumber}", TextToSpeech.QUEUE_FLUSH, null, null) }
            ) {
                Text(contact.pcID.toString(), modifier = Modifier.weight(0.2f))
                Text(contact.userID.toString(), modifier = Modifier.weight(0.2f))
                Text(contact.contactname, modifier = Modifier.weight(0.3f))
                Text(contact.contactnumber, modifier = Modifier.weight(0.3f))
            }
            Divider()
        }
    }
}

@Composable
fun SecondaryContactsTable(contacts: List<SecondaryContact>, tts: TextToSpeech) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(8.dp)) {
                Text("ID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Text("User ID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Text("Name", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                Text("Phone", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
            }
        }
        
        items(contacts) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .clickable { tts.speak("Secondary Contact ID: ${contact.scID}, User ID: ${contact.userID}, Name: ${contact.contactname}, Phone: ${contact.contactnumber}", TextToSpeech.QUEUE_FLUSH, null, null) }
            ) {
                Text(contact.scID.toString(), modifier = Modifier.weight(0.2f))
                Text(contact.userID.toString(), modifier = Modifier.weight(0.2f))
                Text(contact.contactname, modifier = Modifier.weight(0.3f))
                Text(contact.contactnumber, modifier = Modifier.weight(0.3f))
            }
            Divider()
        }
    }
}

@Composable
fun EmptyTableMessage(message: String, tts: TextToSpeech) {
    LaunchedEffect(key1 = message) {
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 18.sp,
            color = Color.Gray
        )
    }
}
