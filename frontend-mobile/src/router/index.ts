import { createRouter, createWebHistory } from 'vue-router';
import MobileShell from '../views/MobileShell.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'mobile-shell',
      component: MobileShell
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
});
