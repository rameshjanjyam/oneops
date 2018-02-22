# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "highline"
  s.version = "1.7.10"

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = ["James Edward Gray II"]
  s.date = "2017-11-24"
  s.description = "A high-level IO library that provides validation, type conversion, and more for\ncommand-line interfaces. HighLine also includes a complete menu system that can\ncrank out anything from simple list selection to complete shells with just\nminutes of work.\n"
  s.email = "james@graysoftinc.com"
  s.extra_rdoc_files = ["README.rdoc", "INSTALL", "TODO", "Changelog.md", "LICENSE"]
  s.files = ["README.rdoc", "INSTALL", "TODO", "Changelog.md", "LICENSE"]
  s.homepage = "https://github.com/JEG2/highline"
  s.licenses = ["Ruby"]
  s.rdoc_options = ["--title", "HighLine Documentation", "--main", "README"]
  s.require_paths = ["lib"]
  s.required_ruby_version = Gem::Requirement.new(">= 1.9.3")
  s.rubyforge_project = "highline"
  s.rubygems_version = "1.8.25"
  s.summary = "HighLine is a high-level command-line IO library."

  if s.respond_to? :specification_version then
    s.specification_version = 4

    if Gem::Version.new(Gem::VERSION) >= Gem::Version.new('1.2.0') then
      s.add_development_dependency(%q<code_statistics>, [">= 0"])
    else
      s.add_dependency(%q<code_statistics>, [">= 0"])
    end
  else
    s.add_dependency(%q<code_statistics>, [">= 0"])
  end
end
