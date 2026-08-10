(() => {
  const button = document.getElementById("join-btn");
  const addressEl = document.getElementById("join-address");
  const toast = document.getElementById("toast");
  if (!button || !toast) return;

  const address = button.dataset.address || "play.glyphmc.net";
  let hideTimer;

  async function copyAddress() {
    try {
      await navigator.clipboard.writeText(address);
    } catch {
      const field = document.createElement("textarea");
      field.value = address;
      field.setAttribute("readonly", "");
      field.style.position = "fixed";
      field.style.opacity = "0";
      document.body.appendChild(field);
      field.select();
      document.execCommand("copy");
      field.remove();
    }

    button.classList.add("is-copied");
    if (addressEl) addressEl.textContent = "Copied!";
    toast.hidden = false;
    requestAnimationFrame(() => toast.classList.add("is-visible"));

    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      toast.classList.remove("is-visible");
      button.classList.remove("is-copied");
      if (addressEl) addressEl.textContent = address;
      setTimeout(() => {
        toast.hidden = true;
      }, 350);
    }, 2200);
  }

  button.addEventListener("click", copyAddress);
})();
