<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from './api'

type User = { id:string; name:string; phoneNumber:string; role:string }
type Account = {
  id:string; ownerId:string; provider:string; accountType:string;
  accountAlias:string; maskedReference:string; availableBalance:number;
  smartPayEnabled:boolean; smartSettlementEnabled:boolean
}
type Provider = { code:string; name:string; mode:string; healthScore:number }
type Payment = {
  id:string; merchantReference:string; amount:number; currency:string;
  sourceAccountId:string; destinationAccountId:string; routeProvider:string;
  status:string; attempts:any[]; candidates:any[]
}

const users = ref<User[]>([])
const providers = ref<Provider[]>([])
const payments = ref<Payment[]>([])
const senderAccounts = ref<Account[]>([])
const receiverAccounts = ref<Account[]>([])
const result = ref<any>(null)

const userForm = reactive({ name:'', phoneNumber:'', role:'INDIVIDUAL' })
const selectedAccountOwner = ref('')
const accountForm = reactive({
  provider:'TELEBIRR',
  accountType:'WALLET',
  accountAlias:'',
  accountReference:'',
  availableBalance:0,
  smartPayEnabled:true,
  smartSettlementEnabled:false
})

const paymentForm = reactive({
  merchantReference:'ORDER-3001',
  amount:2500,
  currency:'ETB',
  senderId:'',
  receiverId:'',
  selectedSourceAccountId:'',
  selectedDestinationAccountId:'',
  smartPay:true,
  smartSettlement:true
})

const senderUsers = computed(() => users.value)
const receiverUsers = computed(() => users.value)

async function loadAll() {
  const [u,p,tx] = await Promise.all([
    api.get('/users'),
    api.get('/providers'),
    api.get('/payments')
  ])
  users.value = u.data
  providers.value = p.data
  payments.value = tx.data
}

async function createUser() {
  await api.post('/users', userForm)
  userForm.name=''; userForm.phoneNumber=''
  await loadAll()
}

async function linkAccount() {
  if (!selectedAccountOwner.value) return
  await api.post(`/users/${selectedAccountOwner.value}/accounts`, accountForm)
  accountForm.accountAlias=''
  accountForm.accountReference=''
  accountForm.availableBalance=0
  await refreshAccounts()
}

async function refreshAccounts() {
  senderAccounts.value = paymentForm.senderId
    ? (await api.get(`/users/${paymentForm.senderId}/accounts`)).data : []
  receiverAccounts.value = paymentForm.receiverId
    ? (await api.get(`/users/${paymentForm.receiverId}/accounts`)).data : []
}

async function createPayment() {
  const { data } = await api.post('/payments', paymentForm, {
    headers: { 'Idempotency-Key': `${paymentForm.merchantReference}-${Date.now()}` }
  })
  result.value = data
  await loadAll()
}

async function setProviderMode(code:string, mode:string) {
  await api.post(`/providers/${code}/mode`, { mode })
  await loadAll()
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header>
      <h1>PayFlow AI</h1>
      <p>Users link their own provider accounts, then PayFlow AI selects the best source, route, and destination.</p>
    </header>

    <main>
      <section class="grid">
        <article class="card">
          <h2>1. Create sender or receiver</h2>
          <input v-model="userForm.name" placeholder="Name" />
          <input v-model="userForm.phoneNumber" placeholder="Phone number" />
          <select v-model="userForm.role">
            <option>INDIVIDUAL</option>
            <option>MERCHANT</option>
          </select>
          <button @click="createUser">Create user</button>
        </article>

        <article class="card">
          <h2>2. Link provider account</h2>
          <select v-model="selectedAccountOwner">
            <option value="">Choose account owner</option>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }} · {{ u.role }}</option>
          </select>
          <input v-model="accountForm.provider" placeholder="Provider, e.g. CBE" />
          <select v-model="accountForm.accountType">
            <option>WALLET</option>
            <option>BANK_ACCOUNT</option>
            <option>CARD</option>
            <option>MERCHANT_ACCOUNT</option>
          </select>
          <input v-model="accountForm.accountAlias" placeholder="Alias" />
          <input v-model="accountForm.accountReference" placeholder="Account number/token" />
          <input v-model.number="accountForm.availableBalance" type="number" placeholder="Balance" />
          <label><input v-model="accountForm.smartPayEnabled" type="checkbox"> Smart Pay eligible</label>
          <label><input v-model="accountForm.smartSettlementEnabled" type="checkbox"> Smart Settlement eligible</label>
          <button @click="linkAccount">Link account</button>
        </article>
      </section>

      <section class="card">
        <h2>3. Create payment</h2>
        <div class="grid">
          <select v-model="paymentForm.senderId" @change="refreshAccounts">
            <option value="">Select sender</option>
            <option v-for="u in senderUsers" :key="u.id" :value="u.id">{{u.name}}</option>
          </select>

          <select v-model="paymentForm.receiverId" @change="refreshAccounts">
            <option value="">Select receiver</option>
            <option v-for="u in receiverUsers" :key="u.id" :value="u.id">{{u.name}}</option>
          </select>

          <input v-model="paymentForm.merchantReference" placeholder="Reference" />
          <input v-model.number="paymentForm.amount" type="number" placeholder="Amount" />
        </div>

        <label><input v-model="paymentForm.smartPay" type="checkbox"> Smart Pay</label>
        <select v-model="paymentForm.selectedSourceAccountId" :disabled="paymentForm.smartPay">
          <option value="">Choose source account</option>
          <option v-for="a in senderAccounts" :key="a.id" :value="a.id">
            {{a.provider}} · {{a.accountAlias}} · {{a.maskedReference}}
          </option>
        </select>

        <label><input v-model="paymentForm.smartSettlement" type="checkbox"> Smart Settlement</label>
        <select v-model="paymentForm.selectedDestinationAccountId" :disabled="paymentForm.smartSettlement">
          <option value="">Choose destination account</option>
          <option v-for="a in receiverAccounts" :key="a.id" :value="a.id">
            {{a.provider}} · {{a.accountAlias}} · {{a.maskedReference}}
          </option>
        </select>

        <button @click="createPayment">Process payment</button>
        <pre v-if="result">{{ JSON.stringify(result, null, 2) }}</pre>
      </section>

      <section class="grid">
        <article class="card">
          <h2>Route providers</h2>
          <div v-for="p in providers" :key="p.code" class="provider-row">
            <span>{{p.name}} · {{p.mode}}</span>
            <select :value="p.mode" @change="setProviderMode(p.code, ($event.target as HTMLSelectElement).value)">
              <option>HEALTHY</option>
              <option>SLOW</option>
              <option>UNSTABLE</option>
              <option>UNAVAILABLE</option>
            </select>
          </div>
        </article>

        <article class="card">
          <h2>Recent payments</h2>
          <div v-for="p in payments" :key="p.id" class="payment-row">
            <b>{{p.merchantReference}}</b>
            <span>{{p.amount}} {{p.currency}}</span>
            <span>{{p.routeProvider}}</span>
            <span :class="p.status === 'SUCCEEDED' ? 'ok' : 'bad'">{{p.status}}</span>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>
