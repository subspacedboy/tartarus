// automatically generated by the FlatBuffers compiler, do not modify


// @generated

use core::mem;
use core::cmp::Ordering;

extern crate flatbuffers;
use self::flatbuffers::{EndianScalar, Follow};

#[allow(unused_imports, dead_code)]
pub mod club {

  use core::mem;
  use core::cmp::Ordering;

  extern crate flatbuffers;
  use self::flatbuffers::{EndianScalar, Follow};
#[allow(unused_imports, dead_code)]
pub mod subjugated {

  use core::mem;
  use core::cmp::Ordering;

  extern crate flatbuffers;
  use self::flatbuffers::{EndianScalar, Follow};
#[allow(unused_imports, dead_code)]
pub mod fb {

  use core::mem;
  use core::cmp::Ordering;

  extern crate flatbuffers;
  use self::flatbuffers::{EndianScalar, Follow};
#[allow(unused_imports, dead_code)]
pub mod message {

  use core::mem;
  use core::cmp::Ordering;

  extern crate flatbuffers;
  use self::flatbuffers::{EndianScalar, Follow};
#[allow(unused_imports, dead_code)]
pub mod configuration {

  use core::mem;
  use core::cmp::Ordering;

  extern crate flatbuffers;
  use self::flatbuffers::{EndianScalar, Follow};

pub enum KeyOffset {}
#[derive(Copy, Clone, PartialEq)]

pub struct Key<'a> {
  pub _tab: flatbuffers::Table<'a>,
}

impl<'a> flatbuffers::Follow<'a> for Key<'a> {
  type Inner = Key<'a>;
  #[inline]
  unsafe fn follow(buf: &'a [u8], loc: usize) -> Self::Inner {
    Self { _tab: flatbuffers::Table::new(buf, loc) }
  }
}

impl<'a> Key<'a> {
  pub const VT_NAME: flatbuffers::VOffsetT = 4;
  pub const VT_PUBLIC_KEY: flatbuffers::VOffsetT = 6;

  #[inline]
  pub unsafe fn init_from_table(table: flatbuffers::Table<'a>) -> Self {
    Key { _tab: table }
  }
  #[allow(unused_mut)]
  pub fn create<'bldr: 'args, 'args: 'mut_bldr, 'mut_bldr, A: flatbuffers::Allocator + 'bldr>(
    _fbb: &'mut_bldr mut flatbuffers::FlatBufferBuilder<'bldr, A>,
    args: &'args KeyArgs<'args>
  ) -> flatbuffers::WIPOffset<Key<'bldr>> {
    let mut builder = KeyBuilder::new(_fbb);
    if let Some(x) = args.public_key { builder.add_public_key(x); }
    if let Some(x) = args.name { builder.add_name(x); }
    builder.finish()
  }


  #[inline]
  pub fn name(&self) -> Option<&'a str> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<&str>>(Key::VT_NAME, None)}
  }
  #[inline]
  pub fn public_key(&self) -> Option<flatbuffers::Vector<'a, u8>> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'a, u8>>>(Key::VT_PUBLIC_KEY, None)}
  }
}

impl flatbuffers::Verifiable for Key<'_> {
  #[inline]
  fn run_verifier(
    v: &mut flatbuffers::Verifier, pos: usize
  ) -> Result<(), flatbuffers::InvalidFlatbuffer> {
    use self::flatbuffers::Verifiable;
    v.visit_table(pos)?
     .visit_field::<flatbuffers::ForwardsUOffset<&str>>("name", Self::VT_NAME, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'_, u8>>>("public_key", Self::VT_PUBLIC_KEY, false)?
     .finish();
    Ok(())
  }
}
pub struct KeyArgs<'a> {
    pub name: Option<flatbuffers::WIPOffset<&'a str>>,
    pub public_key: Option<flatbuffers::WIPOffset<flatbuffers::Vector<'a, u8>>>,
}
impl<'a> Default for KeyArgs<'a> {
  #[inline]
  fn default() -> Self {
    KeyArgs {
      name: None,
      public_key: None,
    }
  }
}

pub struct KeyBuilder<'a: 'b, 'b, A: flatbuffers::Allocator + 'a> {
  fbb_: &'b mut flatbuffers::FlatBufferBuilder<'a, A>,
  start_: flatbuffers::WIPOffset<flatbuffers::TableUnfinishedWIPOffset>,
}
impl<'a: 'b, 'b, A: flatbuffers::Allocator + 'a> KeyBuilder<'a, 'b, A> {
  #[inline]
  pub fn add_name(&mut self, name: flatbuffers::WIPOffset<&'b  str>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(Key::VT_NAME, name);
  }
  #[inline]
  pub fn add_public_key(&mut self, public_key: flatbuffers::WIPOffset<flatbuffers::Vector<'b , u8>>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(Key::VT_PUBLIC_KEY, public_key);
  }
  #[inline]
  pub fn new(_fbb: &'b mut flatbuffers::FlatBufferBuilder<'a, A>) -> KeyBuilder<'a, 'b, A> {
    let start = _fbb.start_table();
    KeyBuilder {
      fbb_: _fbb,
      start_: start,
    }
  }
  #[inline]
  pub fn finish(self) -> flatbuffers::WIPOffset<Key<'a>> {
    let o = self.fbb_.end_table(self.start_);
    flatbuffers::WIPOffset::new(o.value())
  }
}

impl core::fmt::Debug for Key<'_> {
  fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
    let mut ds = f.debug_struct("Key");
      ds.field("name", &self.name());
      ds.field("public_key", &self.public_key());
      ds.finish()
  }
}
pub enum CoordinatorConfigurationOffset {}
#[derive(Copy, Clone, PartialEq)]

pub struct CoordinatorConfiguration<'a> {
  pub _tab: flatbuffers::Table<'a>,
}

impl<'a> flatbuffers::Follow<'a> for CoordinatorConfiguration<'a> {
  type Inner = CoordinatorConfiguration<'a>;
  #[inline]
  unsafe fn follow(buf: &'a [u8], loc: usize) -> Self::Inner {
    Self { _tab: flatbuffers::Table::new(buf, loc) }
  }
}

impl<'a> CoordinatorConfiguration<'a> {
  pub const VT_WEB_URI: flatbuffers::VOffsetT = 4;
  pub const VT_WS_URI: flatbuffers::VOffsetT = 6;
  pub const VT_MQTT_URI: flatbuffers::VOffsetT = 8;
  pub const VT_API_URI: flatbuffers::VOffsetT = 10;
  pub const VT_SAFETY_KEYS: flatbuffers::VOffsetT = 12;
  pub const VT_ENABLE_RESET_COMMAND: flatbuffers::VOffsetT = 14;
  pub const VT_DISABLE_SAFETY_KEYS: flatbuffers::VOffsetT = 16;
  pub const VT_ENABLE_AUXILIARY_SAFETY_KEYS: flatbuffers::VOffsetT = 18;
  pub const VT_AUXILIARY_SAFETY_KEYS: flatbuffers::VOffsetT = 20;
  pub const VT_LOGIN_TOKEN_PUBLIC_KEY: flatbuffers::VOffsetT = 22;

  #[inline]
  pub unsafe fn init_from_table(table: flatbuffers::Table<'a>) -> Self {
    CoordinatorConfiguration { _tab: table }
  }
  #[allow(unused_mut)]
  pub fn create<'bldr: 'args, 'args: 'mut_bldr, 'mut_bldr, A: flatbuffers::Allocator + 'bldr>(
    _fbb: &'mut_bldr mut flatbuffers::FlatBufferBuilder<'bldr, A>,
    args: &'args CoordinatorConfigurationArgs<'args>
  ) -> flatbuffers::WIPOffset<CoordinatorConfiguration<'bldr>> {
    let mut builder = CoordinatorConfigurationBuilder::new(_fbb);
    if let Some(x) = args.login_token_public_key { builder.add_login_token_public_key(x); }
    if let Some(x) = args.auxiliary_safety_keys { builder.add_auxiliary_safety_keys(x); }
    if let Some(x) = args.safety_keys { builder.add_safety_keys(x); }
    if let Some(x) = args.api_uri { builder.add_api_uri(x); }
    if let Some(x) = args.mqtt_uri { builder.add_mqtt_uri(x); }
    if let Some(x) = args.ws_uri { builder.add_ws_uri(x); }
    if let Some(x) = args.web_uri { builder.add_web_uri(x); }
    builder.add_enable_auxiliary_safety_keys(args.enable_auxiliary_safety_keys);
    builder.add_disable_safety_keys(args.disable_safety_keys);
    builder.add_enable_reset_command(args.enable_reset_command);
    builder.finish()
  }


  #[inline]
  pub fn web_uri(&self) -> Option<&'a str> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<&str>>(CoordinatorConfiguration::VT_WEB_URI, None)}
  }
  #[inline]
  pub fn ws_uri(&self) -> Option<&'a str> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<&str>>(CoordinatorConfiguration::VT_WS_URI, None)}
  }
  #[inline]
  pub fn mqtt_uri(&self) -> Option<&'a str> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<&str>>(CoordinatorConfiguration::VT_MQTT_URI, None)}
  }
  #[inline]
  pub fn api_uri(&self) -> Option<&'a str> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<&str>>(CoordinatorConfiguration::VT_API_URI, None)}
  }
  #[inline]
  pub fn safety_keys(&self) -> Option<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key<'a>>>> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key>>>>(CoordinatorConfiguration::VT_SAFETY_KEYS, None)}
  }
  #[inline]
  pub fn enable_reset_command(&self) -> bool {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<bool>(CoordinatorConfiguration::VT_ENABLE_RESET_COMMAND, Some(false)).unwrap()}
  }
  #[inline]
  pub fn disable_safety_keys(&self) -> bool {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<bool>(CoordinatorConfiguration::VT_DISABLE_SAFETY_KEYS, Some(false)).unwrap()}
  }
  #[inline]
  pub fn enable_auxiliary_safety_keys(&self) -> bool {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<bool>(CoordinatorConfiguration::VT_ENABLE_AUXILIARY_SAFETY_KEYS, Some(false)).unwrap()}
  }
  #[inline]
  pub fn auxiliary_safety_keys(&self) -> Option<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key<'a>>>> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key>>>>(CoordinatorConfiguration::VT_AUXILIARY_SAFETY_KEYS, None)}
  }
  #[inline]
  pub fn login_token_public_key(&self) -> Option<flatbuffers::Vector<'a, i8>> {
    // Safety:
    // Created from valid Table for this object
    // which contains a valid value in this slot
    unsafe { self._tab.get::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'a, i8>>>(CoordinatorConfiguration::VT_LOGIN_TOKEN_PUBLIC_KEY, None)}
  }
}

impl flatbuffers::Verifiable for CoordinatorConfiguration<'_> {
  #[inline]
  fn run_verifier(
    v: &mut flatbuffers::Verifier, pos: usize
  ) -> Result<(), flatbuffers::InvalidFlatbuffer> {
    use self::flatbuffers::Verifiable;
    v.visit_table(pos)?
     .visit_field::<flatbuffers::ForwardsUOffset<&str>>("web_uri", Self::VT_WEB_URI, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<&str>>("ws_uri", Self::VT_WS_URI, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<&str>>("mqtt_uri", Self::VT_MQTT_URI, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<&str>>("api_uri", Self::VT_API_URI, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'_, flatbuffers::ForwardsUOffset<Key>>>>("safety_keys", Self::VT_SAFETY_KEYS, false)?
     .visit_field::<bool>("enable_reset_command", Self::VT_ENABLE_RESET_COMMAND, false)?
     .visit_field::<bool>("disable_safety_keys", Self::VT_DISABLE_SAFETY_KEYS, false)?
     .visit_field::<bool>("enable_auxiliary_safety_keys", Self::VT_ENABLE_AUXILIARY_SAFETY_KEYS, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'_, flatbuffers::ForwardsUOffset<Key>>>>("auxiliary_safety_keys", Self::VT_AUXILIARY_SAFETY_KEYS, false)?
     .visit_field::<flatbuffers::ForwardsUOffset<flatbuffers::Vector<'_, i8>>>("login_token_public_key", Self::VT_LOGIN_TOKEN_PUBLIC_KEY, false)?
     .finish();
    Ok(())
  }
}
pub struct CoordinatorConfigurationArgs<'a> {
    pub web_uri: Option<flatbuffers::WIPOffset<&'a str>>,
    pub ws_uri: Option<flatbuffers::WIPOffset<&'a str>>,
    pub mqtt_uri: Option<flatbuffers::WIPOffset<&'a str>>,
    pub api_uri: Option<flatbuffers::WIPOffset<&'a str>>,
    pub safety_keys: Option<flatbuffers::WIPOffset<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key<'a>>>>>,
    pub enable_reset_command: bool,
    pub disable_safety_keys: bool,
    pub enable_auxiliary_safety_keys: bool,
    pub auxiliary_safety_keys: Option<flatbuffers::WIPOffset<flatbuffers::Vector<'a, flatbuffers::ForwardsUOffset<Key<'a>>>>>,
    pub login_token_public_key: Option<flatbuffers::WIPOffset<flatbuffers::Vector<'a, i8>>>,
}
impl<'a> Default for CoordinatorConfigurationArgs<'a> {
  #[inline]
  fn default() -> Self {
    CoordinatorConfigurationArgs {
      web_uri: None,
      ws_uri: None,
      mqtt_uri: None,
      api_uri: None,
      safety_keys: None,
      enable_reset_command: false,
      disable_safety_keys: false,
      enable_auxiliary_safety_keys: false,
      auxiliary_safety_keys: None,
      login_token_public_key: None,
    }
  }
}

pub struct CoordinatorConfigurationBuilder<'a: 'b, 'b, A: flatbuffers::Allocator + 'a> {
  fbb_: &'b mut flatbuffers::FlatBufferBuilder<'a, A>,
  start_: flatbuffers::WIPOffset<flatbuffers::TableUnfinishedWIPOffset>,
}
impl<'a: 'b, 'b, A: flatbuffers::Allocator + 'a> CoordinatorConfigurationBuilder<'a, 'b, A> {
  #[inline]
  pub fn add_web_uri(&mut self, web_uri: flatbuffers::WIPOffset<&'b  str>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_WEB_URI, web_uri);
  }
  #[inline]
  pub fn add_ws_uri(&mut self, ws_uri: flatbuffers::WIPOffset<&'b  str>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_WS_URI, ws_uri);
  }
  #[inline]
  pub fn add_mqtt_uri(&mut self, mqtt_uri: flatbuffers::WIPOffset<&'b  str>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_MQTT_URI, mqtt_uri);
  }
  #[inline]
  pub fn add_api_uri(&mut self, api_uri: flatbuffers::WIPOffset<&'b  str>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_API_URI, api_uri);
  }
  #[inline]
  pub fn add_safety_keys(&mut self, safety_keys: flatbuffers::WIPOffset<flatbuffers::Vector<'b , flatbuffers::ForwardsUOffset<Key<'b >>>>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_SAFETY_KEYS, safety_keys);
  }
  #[inline]
  pub fn add_enable_reset_command(&mut self, enable_reset_command: bool) {
    self.fbb_.push_slot::<bool>(CoordinatorConfiguration::VT_ENABLE_RESET_COMMAND, enable_reset_command, false);
  }
  #[inline]
  pub fn add_disable_safety_keys(&mut self, disable_safety_keys: bool) {
    self.fbb_.push_slot::<bool>(CoordinatorConfiguration::VT_DISABLE_SAFETY_KEYS, disable_safety_keys, false);
  }
  #[inline]
  pub fn add_enable_auxiliary_safety_keys(&mut self, enable_auxiliary_safety_keys: bool) {
    self.fbb_.push_slot::<bool>(CoordinatorConfiguration::VT_ENABLE_AUXILIARY_SAFETY_KEYS, enable_auxiliary_safety_keys, false);
  }
  #[inline]
  pub fn add_auxiliary_safety_keys(&mut self, auxiliary_safety_keys: flatbuffers::WIPOffset<flatbuffers::Vector<'b , flatbuffers::ForwardsUOffset<Key<'b >>>>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_AUXILIARY_SAFETY_KEYS, auxiliary_safety_keys);
  }
  #[inline]
  pub fn add_login_token_public_key(&mut self, login_token_public_key: flatbuffers::WIPOffset<flatbuffers::Vector<'b , i8>>) {
    self.fbb_.push_slot_always::<flatbuffers::WIPOffset<_>>(CoordinatorConfiguration::VT_LOGIN_TOKEN_PUBLIC_KEY, login_token_public_key);
  }
  #[inline]
  pub fn new(_fbb: &'b mut flatbuffers::FlatBufferBuilder<'a, A>) -> CoordinatorConfigurationBuilder<'a, 'b, A> {
    let start = _fbb.start_table();
    CoordinatorConfigurationBuilder {
      fbb_: _fbb,
      start_: start,
    }
  }
  #[inline]
  pub fn finish(self) -> flatbuffers::WIPOffset<CoordinatorConfiguration<'a>> {
    let o = self.fbb_.end_table(self.start_);
    flatbuffers::WIPOffset::new(o.value())
  }
}

impl core::fmt::Debug for CoordinatorConfiguration<'_> {
  fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
    let mut ds = f.debug_struct("CoordinatorConfiguration");
      ds.field("web_uri", &self.web_uri());
      ds.field("ws_uri", &self.ws_uri());
      ds.field("mqtt_uri", &self.mqtt_uri());
      ds.field("api_uri", &self.api_uri());
      ds.field("safety_keys", &self.safety_keys());
      ds.field("enable_reset_command", &self.enable_reset_command());
      ds.field("disable_safety_keys", &self.disable_safety_keys());
      ds.field("enable_auxiliary_safety_keys", &self.enable_auxiliary_safety_keys());
      ds.field("auxiliary_safety_keys", &self.auxiliary_safety_keys());
      ds.field("login_token_public_key", &self.login_token_public_key());
      ds.finish()
  }
}
#[inline]
/// Verifies that a buffer of bytes contains a `CoordinatorConfiguration`
/// and returns it.
/// Note that verification is still experimental and may not
/// catch every error, or be maximally performant. For the
/// previous, unchecked, behavior use
/// `root_as_coordinator_configuration_unchecked`.
pub fn root_as_coordinator_configuration(buf: &[u8]) -> Result<CoordinatorConfiguration, flatbuffers::InvalidFlatbuffer> {
  flatbuffers::root::<CoordinatorConfiguration>(buf)
}
#[inline]
/// Verifies that a buffer of bytes contains a size prefixed
/// `CoordinatorConfiguration` and returns it.
/// Note that verification is still experimental and may not
/// catch every error, or be maximally performant. For the
/// previous, unchecked, behavior use
/// `size_prefixed_root_as_coordinator_configuration_unchecked`.
pub fn size_prefixed_root_as_coordinator_configuration(buf: &[u8]) -> Result<CoordinatorConfiguration, flatbuffers::InvalidFlatbuffer> {
  flatbuffers::size_prefixed_root::<CoordinatorConfiguration>(buf)
}
#[inline]
/// Verifies, with the given options, that a buffer of bytes
/// contains a `CoordinatorConfiguration` and returns it.
/// Note that verification is still experimental and may not
/// catch every error, or be maximally performant. For the
/// previous, unchecked, behavior use
/// `root_as_coordinator_configuration_unchecked`.
pub fn root_as_coordinator_configuration_with_opts<'b, 'o>(
  opts: &'o flatbuffers::VerifierOptions,
  buf: &'b [u8],
) -> Result<CoordinatorConfiguration<'b>, flatbuffers::InvalidFlatbuffer> {
  flatbuffers::root_with_opts::<CoordinatorConfiguration<'b>>(opts, buf)
}
#[inline]
/// Verifies, with the given verifier options, that a buffer of
/// bytes contains a size prefixed `CoordinatorConfiguration` and returns
/// it. Note that verification is still experimental and may not
/// catch every error, or be maximally performant. For the
/// previous, unchecked, behavior use
/// `root_as_coordinator_configuration_unchecked`.
pub fn size_prefixed_root_as_coordinator_configuration_with_opts<'b, 'o>(
  opts: &'o flatbuffers::VerifierOptions,
  buf: &'b [u8],
) -> Result<CoordinatorConfiguration<'b>, flatbuffers::InvalidFlatbuffer> {
  flatbuffers::size_prefixed_root_with_opts::<CoordinatorConfiguration<'b>>(opts, buf)
}
#[inline]
/// Assumes, without verification, that a buffer of bytes contains a CoordinatorConfiguration and returns it.
/// # Safety
/// Callers must trust the given bytes do indeed contain a valid `CoordinatorConfiguration`.
pub unsafe fn root_as_coordinator_configuration_unchecked(buf: &[u8]) -> CoordinatorConfiguration {
  flatbuffers::root_unchecked::<CoordinatorConfiguration>(buf)
}
#[inline]
/// Assumes, without verification, that a buffer of bytes contains a size prefixed CoordinatorConfiguration and returns it.
/// # Safety
/// Callers must trust the given bytes do indeed contain a valid size prefixed `CoordinatorConfiguration`.
pub unsafe fn size_prefixed_root_as_coordinator_configuration_unchecked(buf: &[u8]) -> CoordinatorConfiguration {
  flatbuffers::size_prefixed_root_unchecked::<CoordinatorConfiguration>(buf)
}
#[inline]
pub fn finish_coordinator_configuration_buffer<'a, 'b, A: flatbuffers::Allocator + 'a>(
    fbb: &'b mut flatbuffers::FlatBufferBuilder<'a, A>,
    root: flatbuffers::WIPOffset<CoordinatorConfiguration<'a>>) {
  fbb.finish(root, None);
}

#[inline]
pub fn finish_size_prefixed_coordinator_configuration_buffer<'a, 'b, A: flatbuffers::Allocator + 'a>(fbb: &'b mut flatbuffers::FlatBufferBuilder<'a, A>, root: flatbuffers::WIPOffset<CoordinatorConfiguration<'a>>) {
  fbb.finish_size_prefixed(root, None);
}
}  // pub mod configuration
}  // pub mod message
}  // pub mod fb
}  // pub mod subjugated
}  // pub mod club

