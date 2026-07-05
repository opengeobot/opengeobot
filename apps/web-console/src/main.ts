// Function: Vue application entry point
// Time: 2026-07-04
// Author: AxeXie
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { permissionDirective } from './directives/permission'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.directive('permission', permissionDirective)
app.mount('#app')
