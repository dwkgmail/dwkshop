import { createRouter, createWebHistory } from 'vue-router';
import AdminShell from '../views/AdminShell.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'admin-shell',
      component: AdminShell
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
});
