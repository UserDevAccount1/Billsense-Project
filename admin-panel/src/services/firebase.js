import { initializeApp } from 'firebase/app'
import { getDatabase, ref, get } from 'firebase/database'
import { getStorage } from 'firebase/storage'

const firebaseConfig = {
  apiKey: 'AIzaSyCKdSYeVztx0gXo2Z-Q6CkZ_SJT2pcajAI',
  authDomain: 'bill-sense-aec6b.firebaseapp.com',
  databaseURL: 'https://bill-sense-aec6b-default-rtdb.firebaseio.com',
  projectId: 'bill-sense-aec6b',
  storageBucket: 'bill-sense-aec6b.firebasestorage.app',
  messagingSenderId: '340624938055',
  appId: '1:340624938055:android:81d528ded5f924a23fcd62'
}

const app = initializeApp(firebaseConfig)
const database = getDatabase(app)
const storage = getStorage(app)

export { app, database, storage, ref, get }
