const dry = true;
const gmailOrProton = ['protonmail.com', 'protonmail.ch', 'pm.me', 'proton.me', 'gmail.com', 'googlemail.com'];

function normalize(email) {
  let [name, domain] = email.toLowerCase().split('@');
  [name] = name.split('+');

  if (gmailOrProton.includes(domain)) name = name.replace(/\./g, '');

  return name + '@' + domain;
}

db.user4
  .find({ email: /^[^+]+\+.*@.+$/ }, { email: 1, verbatimEmail: 1, username: 1 })
  .forEach(user => {
    const normalized = normalize(user.email);
    const verbatim = user.verbatimEmail || user.email;
    print(user.username, ': ', verbatim, '->', normalized);

    if (!dry && user.email != normalized) db.user4.update(
      { _id: user._id },
      {
        $set: {
          email: normalized,
          verbatimEmail: verbatim,
        },
      },
    );
  });
